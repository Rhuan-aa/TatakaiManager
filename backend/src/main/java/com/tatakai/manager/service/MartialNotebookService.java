package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.ForgeStartRequest;
import com.tatakai.manager.dto.request.NotebookDraftRequest;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.dto.response.AssemblyCheck;
import com.tatakai.manager.dto.response.NotebookResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.exception.SheetEntryNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterArtRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.CharacterTechniqueRepository;
import com.tatakai.manager.repository.ForgeRepository;
import com.tatakai.manager.repository.NotebookDraftRepository;
import com.tatakai.manager.service.rules.AssemblyRules;
import com.tatakai.manager.service.rules.MasteryRules;
import com.tatakai.manager.service.rules.ProgressionRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

/** Caderno Marcial e Forja (F4c, PLANO §19): rascunho aprovado pelo Mestre vira técnica/base avulsa em Esboço. */
@Service
public class MartialNotebookService {

    public static final int MAX_DRAFTS = 3;
    /** Rascunhos do Caderno usam os tetos de Arte Criada. */
    private static final ArtCategory DRAFT_CATEGORY = ArtCategory.CREATED;

    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final CharacterArtRepository characterArtRepository;
    private final CharacterTechniqueRepository techniqueRepository;
    private final NotebookDraftRepository draftRepository;
    private final ForgeRepository forgeRepository;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher events;

    private record Ctx(CharacterSheet sheet, UUID actorId, String actorName, boolean master, boolean owner) {
        ActorRole role() {
            return master ? ActorRole.MASTER : ActorRole.PLAYER;
        }
    }

    public MartialNotebookService(CharacterSheetRepository sheetRepository, CampaignMemberRepository memberRepository,
                                  CharacterArtRepository characterArtRepository,
                                  CharacterTechniqueRepository techniqueRepository,
                                  NotebookDraftRepository draftRepository, ForgeRepository forgeRepository,
                                  SheetActivityLogService activityLog, ApplicationEventPublisher events) {
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.characterArtRepository = characterArtRepository;
        this.techniqueRepository = techniqueRepository;
        this.draftRepository = draftRepository;
        this.forgeRepository = forgeRepository;
        this.activityLog = activityLog;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public NotebookResponse get(UUID sheetId, UUID requesterId) {
        return response(load(sheetId, requesterId).sheet());
    }

    // ---------------------------------------------------------------- Caderno

    @Transactional
    public NotebookResponse create(UUID sheetId, NotebookDraftRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (draftRepository.findBySheetIdOrderByCreatedAt(sheetId).size() >= MAX_DRAFTS) {
            throw new InvalidCharacterSheetException("O Caderno comporta no máximo " + MAX_DRAFTS + " rascunhos");
        }
        NotebookDraft d = NotebookDraft.builder().sheet(c.sheet()).status(NotebookStatus.DRAFT).build();
        fill(d, req);
        draftRepository.save(d);
        return finish(c, "NOTEBOOK_CREATE", null, d.getName());
    }

    @Transactional
    public NotebookResponse update(UUID sheetId, UUID draftId, NotebookDraftRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        NotebookDraft d = draft(c, draftId);
        if (d.getStatus() != NotebookStatus.DRAFT) throw new InvalidCharacterSheetException("Só rascunhos em edição mudam");
        fill(d, req);
        return finish(c, "NOTEBOOK_UPDATE", null, d.getName());
    }

    @Transactional
    public NotebookResponse delete(UUID sheetId, UUID draftId, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        NotebookDraft d = draft(c, draftId);
        if (forgeRepository.findBySheetIdAndCompletedAtIsNull(sheetId).filter(f -> f.getDraftId().equals(d.getId())).isPresent()) {
            throw new InvalidCharacterSheetException("O rascunho está na Forja; abandone a Forja antes");
        }
        draftRepository.delete(d);
        return finish(c, "NOTEBOOK_DELETE", d.getName(), null);
    }

    @Transactional
    public NotebookResponse submit(UUID sheetId, UUID draftId, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        NotebookDraft d = draft(c, draftId);
        if (d.getStatus() != NotebookStatus.DRAFT) throw new InvalidCharacterSheetException("O rascunho já foi enviado");
        AssemblyCheck check = check(d);
        if (!check.issues().isEmpty()) {
            throw new InvalidCharacterSheetException("Corrija a montagem: " + String.join("; ", check.issues()));
        }
        d.setStatus(NotebookStatus.PENDING_APPROVAL);
        return finish(c, "NOTEBOOK_SUBMIT", NotebookStatus.DRAFT, NotebookStatus.PENDING_APPROVAL);
    }

    @Transactional
    public NotebookResponse review(UUID sheetId, UUID draftId, ReviewArtRequest req, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        NotebookDraft d = draft(c, draftId);
        if (d.getStatus() != NotebookStatus.PENDING_APPROVAL) {
            throw new InvalidCharacterSheetException("O rascunho não está aguardando aprovação");
        }
        d.setStatus(Boolean.TRUE.equals(req.approved()) ? NotebookStatus.APPROVED : NotebookStatus.DRAFT);
        d.setReviewNote(req.reason());
        return finish(c, Boolean.TRUE.equals(req.approved()) ? "NOTEBOOK_APPROVE" : "NOTEBOOK_REJECT",
                NotebookStatus.PENDING_APPROVAL, d.getStatus());
    }

    // ---------------------------------------------------------------- Forja

    @Transactional
    public NotebookResponse startForge(UUID sheetId, ForgeStartRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        CharacterSheet s = c.sheet();
        if (forgeRepository.findBySheetIdAndCompletedAtIsNull(sheetId).isPresent()) {
            throw new InvalidCharacterSheetException("Já há uma Forja em andamento");
        }
        NotebookDraft d = draft(c, req.draftId());
        if (d.getStatus() != NotebookStatus.APPROVED) {
            throw new InvalidCharacterSheetException("Só rascunho aprovado pelo Mestre vai à Forja");
        }
        Graduation g = d.getGraduation();
        Graduation highest = characterArtRepository.findBySheetIdOrderByCreatedAt(sheetId).stream()
                .filter(a -> a.getStatus() == CharacterArtStatus.ACTIVE).map(CharacterArt::getGraduation)
                .max(Enum::compareTo).orElse(Graduation.GENERIC);
        if (g.compareTo(highest) > 0) {
            throw new InvalidCharacterSheetException("Graduação do rascunho acima da maior graduação do lutador");
        }
        KeikenRank need = ProgressionRules.forgeKeiken(g);
        if (ProgressionRules.keiken(s.getFightExperience()).compareTo(need) < 0) {
            throw new InvalidCharacterSheetException("Keiken insuficiente para forjar nesta graduação (" + need + ")");
        }
        int required = 3 * check(d).finalComplexity();
        forgeRepository.save(Forge.builder().sheet(s).draftId(d.getId()).name(d.getName())
                .requiredPoints(required).accumulatedPoints(0).build());
        return finish(c, "FORGE_START", null, d.getName() + " (0/" + required + ")");
    }

    @Transactional
    public NotebookResponse abandonForge(UUID sheetId, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        Forge f = forgeRepository.findBySheetIdAndCompletedAtIsNull(sheetId).orElseThrow(SheetEntryNotFoundException::new);
        forgeRepository.delete(f);
        return finish(c, "FORGE_ABANDON", f.getName(), null);
    }

    /**
     * Sessão de Forja vinda do TimeSkip, sem checagem de acesso: soma 1 + floor(modQ(TAL)/2); ao completar cria a
     * técnica/base avulsa em Esboço e consome o rascunho. Devolve o texto do resultado, ou null sem Forja em andamento.
     */
    public String applySession(CharacterSheet s) {
        Forge f = forgeRepository.findBySheetIdAndCompletedAtIsNull(s.getId()).orElse(null);
        if (f == null) return null;
        f.setAccumulatedPoints(Math.min(f.getRequiredPoints(),
                f.getAccumulatedPoints() + MasteryRules.trainingGain(s.getTal())));
        if (f.getAccumulatedPoints() < f.getRequiredPoints()) {
            return "Forja " + f.getName() + " " + f.getAccumulatedPoints() + "/" + f.getRequiredPoints();
        }
        f.setCompletedAt(Instant.now());
        NotebookDraft d = draftRepository.findById(f.getDraftId()).orElse(null);
        if (d != null) {
            AssemblyCheck check = check(d);
            techniqueRepository.save(CharacterTechnique.builder().sheet(s).kind(d.getKind()).name(d.getName())
                    .graduation(d.getGraduation()).complexity(check.finalComplexity())
                    .trainingPoints(check.trainingPoints()).trainingProgress(check.trainingPoints())
                    .state(TechniqueState.ACTIVE).mastery(Mastery.SKETCH).masteryPoints(0)
                    .bonuses(new ArrayList<>()).build());
            draftRepository.delete(d);
        }
        return "Forja concluída: " + f.getName() + " (Esboço)";
    }

    // ---------------------------------------------------------------- apoio

    private static void fill(NotebookDraft d, NotebookDraftRequest req) {
        String name = req.kind() == MoveKind.BASE ? (req.base() == null ? null : req.base().getName())
                : (req.technique() == null ? null : req.technique().getName());
        if (name == null || name.isBlank() || name.length() > 100 || name.matches(".*[<>].*")) {
            throw new InvalidCharacterSheetException("Informe um nome válido (até 100 caracteres, sem < e >)");
        }
        d.setKind(req.kind());
        d.setTechnique(req.kind() == MoveKind.TECHNIQUE ? req.technique() : null);
        d.setBase(req.kind() == MoveKind.BASE ? req.base() : null);
    }

    private static AssemblyCheck check(NotebookDraft d) {
        return d.getKind() == MoveKind.BASE ? AssemblyRules.checkBase(DRAFT_CATEGORY, d.getBase())
                : AssemblyRules.checkTechnique(DRAFT_CATEGORY, d.getTechnique());
    }

    private NotebookDraft draft(Ctx c, UUID id) {
        return draftRepository.findById(id).filter(d -> d.getSheet().getId().equals(c.sheet().getId()))
                .orElseThrow(SheetEntryNotFoundException::new);
    }

    /** Dono e Mestre; ficha de NPC: só o dono do NPC (age como Mestre), outros 404. */
    private Ctx load(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        if (sheet.isNpcSheet()) {
            if (!requesterId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
            return new Ctx(sheet, requesterId, null, true, true);
        }
        CampaignMember m = memberRepository.findByCampaignIdAndUserId(
                        sheet.getCampaignMember().getCampaign().getId(), requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        boolean master = m.getRole() == Role.MASTER;
        boolean owner = sheet.getCampaignMember().getUser().getId().equals(requesterId);
        if (!master && !owner) throw new AccessDeniedException("Apenas o dono da ficha e o Mestre acessam o Caderno");
        return new Ctx(sheet, requesterId, m.getUser().getName(), master, owner);
    }

    private Ctx master(UUID sheetId, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (!c.master()) throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
        return c;
    }

    private NotebookResponse finish(Ctx c, String action, Object before, Object after) {
        CharacterSheet s = c.sheet();
        activityLog.record(s, c.actorId(), c.role(), ActivityCategory.PROGRESSION, action, "notebook", before, after,
                null, null);
        if (!s.isNpcSheet()) {
            events.publishEvent(new SheetUpdateMessage(SheetUpdateMessage.SheetEvent.SHEET_UPDATED,
                    s.getCampaignMember().getCampaign().getId(), s.getId(), ActivityCategory.PROGRESSION, action,
                    c.actorName()));
        }
        return response(s);
    }

    private NotebookResponse response(CharacterSheet s) {
        var drafts = draftRepository.findBySheetIdOrderByCreatedAt(s.getId()).stream()
                .map(d -> new NotebookResponse.Draft(d.getId(), d.getKind(), d.getName(), d.getGraduation(),
                        d.getTechnique(), d.getBase(), d.getStatus(), d.getReviewNote(), check(d), d.getCreatedAt()))
                .toList();
        var forge = forgeRepository.findBySheetIdAndCompletedAtIsNull(s.getId())
                .map(f -> new NotebookResponse.ForgeView(f.getId(), f.getDraftId(), f.getName(), f.getRequiredPoints(),
                        f.getAccumulatedPoints(), f.getStartedAt())).orElse(null);
        return new NotebookResponse(drafts, forge);
    }
}
