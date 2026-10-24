package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CharacterArtRequest;
import com.tatakai.manager.dto.request.GraduationRequest;
import com.tatakai.manager.dto.request.LearnMoveRequest;
import com.tatakai.manager.dto.request.LevelUpRequest;
import com.tatakai.manager.dto.request.MasteryRequest;
import com.tatakai.manager.dto.request.PointsRequest;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.dto.request.TechniqueStateRequest;
import com.tatakai.manager.dto.response.CharacterArtResponse;
import com.tatakai.manager.dto.response.CharacterTechniqueResponse;
import com.tatakai.manager.dto.response.SheetMartialResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.exception.SheetEntryNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignNpcRepository;
import com.tatakai.manager.repository.CharacterArtRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.CharacterTechniqueRepository;
import com.tatakai.manager.repository.MartialArtRepository;
import com.tatakai.manager.service.rules.AssemblyRules;
import com.tatakai.manager.service.rules.MasteryRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Artes e técnicas na ficha + maestria (F3b, PLANO §16). Jogador pede; Mestre aprova e lança pontos. */
@Service
public class CharacterTechniqueService {

    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final CampaignNpcRepository campaignNpcRepository;
    private final CharacterArtRepository characterArtRepository;
    private final CharacterTechniqueRepository techniqueRepository;
    private final MartialArtRepository martialArtRepository;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher events;

    private record Ctx(CharacterSheet sheet, UUID actorId, String actorName, boolean master) {
        ActorRole role() {
            return master ? ActorRole.MASTER : ActorRole.PLAYER;
        }
    }

    public CharacterTechniqueService(CharacterSheetRepository sheetRepository,
                                     CampaignMemberRepository memberRepository,
                                     CampaignNpcRepository campaignNpcRepository,
                                     CharacterArtRepository characterArtRepository,
                                     CharacterTechniqueRepository techniqueRepository,
                                     MartialArtRepository martialArtRepository,
                                     SheetActivityLogService activityLog,
                                     ApplicationEventPublisher events) {
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.campaignNpcRepository = campaignNpcRepository;
        this.characterArtRepository = characterArtRepository;
        this.techniqueRepository = techniqueRepository;
        this.martialArtRepository = martialArtRepository;
        this.activityLog = activityLog;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public SheetMartialResponse get(UUID sheetId, UUID requesterId) {
        return response(load(sheetId, requesterId).sheet());
    }

    // ---------------------------------------------------------------- artes

    @Transactional
    public SheetMartialResponse requestArt(UUID sheetId, CharacterArtRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        MartialArt art = martialArtRepository.findById(req.artId()).orElseThrow(SheetEntryNotFoundException::new);
        if (art.getStatus() != null && art.getStatus() != ArtStatus.APPROVED) {
            throw new InvalidCharacterSheetException("A arte ainda não foi aprovada no catálogo");
        }
        if (characterArtRepository.existsBySheetIdAndArtId(sheetId, art.getId())) {
            throw new InvalidCharacterSheetException("A ficha já possui esta arte");
        }
        characterArtRepository.save(CharacterArt.builder().sheet(c.sheet()).artId(art.getId()).artName(art.getName())
                .category(art.getCategory()).graduation(Graduation.BEGINNER)
                .status(c.master() ? CharacterArtStatus.ACTIVE : CharacterArtStatus.REQUESTED).build());
        return finish(c, c.master() ? "ADD_ART" : "REQUEST_ART", "art", null, art.getName());
    }

    @Transactional
    public SheetMartialResponse reviewArt(UUID sheetId, UUID characterArtId, ReviewArtRequest req, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterArt ca = art(c, characterArtId);
        if (ca.getStatus() != CharacterArtStatus.REQUESTED) {
            throw new InvalidCharacterSheetException("A arte não está aguardando aprovação");
        }
        if (Boolean.TRUE.equals(req.approved())) {
            ca.setStatus(CharacterArtStatus.ACTIVE);
            return finish(c, "APPROVE_ART", "art", null, ca.getArtName());
        }
        characterArtRepository.delete(ca);
        return finish(c, "REJECT_ART", "art", ca.getArtName(), null);
    }

    @Transactional
    public SheetMartialResponse setGraduation(UUID sheetId, UUID characterArtId, GraduationRequest req, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterArt ca = art(c, characterArtId);
        Graduation before = ca.getGraduation();
        ca.setGraduation(req.graduation());
        return finish(c, "SET_ART_GRADUATION", "art:" + ca.getArtName(), before, req.graduation());
    }

    @Transactional
    public SheetMartialResponse removeArt(UUID sheetId, UUID characterArtId, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterArt ca = art(c, characterArtId);
        techniqueRepository.deleteAll(techniqueRepository.findByCharacterArtId(ca.getId()));
        characterArtRepository.delete(ca);
        return finish(c, "REMOVE_ART", "art", ca.getArtName(), null);
    }

    // ---------------------------------------------------------------- técnicas e bases

    @Transactional
    public SheetMartialResponse learn(UUID sheetId, LearnMoveRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        CharacterArt ca = art(c, req.characterArtId());
        if (ca.getStatus() != CharacterArtStatus.ACTIVE) {
            throw new InvalidCharacterSheetException("A arte ainda não foi aprovada pelo Mestre");
        }
        MartialArt art = martialArtRepository.findById(ca.getArtId()).orElseThrow(SheetEntryNotFoundException::new);
        Move move = findMove(art, req.kind(), req.name());
        if (move.graduation().ordinal() > ca.getGraduation().ordinal()) {
            throw new InvalidCharacterSheetException("Graduação da arte insuficiente para " + move.name());
        }
        boolean already = techniqueRepository.findBySheetIdOrderByCreatedAt(sheetId).stream()
                .anyMatch(t -> t.getCharacterArt().getId().equals(ca.getId()) && t.getKind() == req.kind()
                        && t.getName().equals(move.name()));
        if (already) throw new InvalidCharacterSheetException("A ficha já possui " + move.name());

        techniqueRepository.save(CharacterTechnique.builder().sheet(c.sheet()).characterArt(ca).kind(req.kind())
                .name(move.name()).graduation(move.graduation()).complexity(move.complexity())
                .trainingPoints(move.trainingPoints()).trainingProgress(0)
                .state(c.master() ? TechniqueState.IN_TRAINING : TechniqueState.REQUESTED)
                .mastery(Mastery.STANDARD).masteryPoints(0).bonuses(new ArrayList<>()).build());
        return finish(c, c.master() ? "ADD_TECHNIQUE" : "REQUEST_TECHNIQUE", "technique", null, move.name());
    }

    @Transactional
    public SheetMartialResponse reviewTechnique(UUID sheetId, UUID techniqueId, ReviewArtRequest req, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterTechnique t = technique(c, techniqueId);
        if (t.getState() != TechniqueState.REQUESTED) {
            throw new InvalidCharacterSheetException("A técnica não está aguardando aprovação");
        }
        if (Boolean.TRUE.equals(req.approved())) {
            t.setState(TechniqueState.IN_TRAINING);
            return finish(c, "APPROVE_TECHNIQUE", "technique:" + t.getName(), TechniqueState.REQUESTED,
                    TechniqueState.IN_TRAINING);
        }
        techniqueRepository.delete(t);
        return finish(c, "REJECT_TECHNIQUE", "technique", t.getName(), null);
    }

    /** Pontos de treino; ao completar o PT entra ativa (ou guardada, sem espaço). F4 automatiza via TimeSkip. */
    @Transactional
    public SheetMartialResponse train(UUID sheetId, UUID techniqueId, PointsRequest req, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterTechnique t = technique(c, techniqueId);
        if (t.getState() != TechniqueState.IN_TRAINING) {
            throw new InvalidCharacterSheetException("A técnica não está em treino");
        }
        if (req.points() <= 0) throw new InvalidCharacterSheetException("Informe pontos de treino positivos");
        int before = t.getTrainingProgress();
        t.setTrainingProgress(Math.min(t.getTrainingPoints(), before + req.points()));
        if (t.getTrainingProgress() >= t.getTrainingPoints()) {
            List<CharacterTechnique> all = techniqueRepository.findBySheetIdOrderByCreatedAt(sheetId);
            if (hasRoom(all, t, TechniqueState.ACTIVE)) t.setState(TechniqueState.ACTIVE);
            else if (hasRoom(all, t, TechniqueState.STORED)) t.setState(TechniqueState.STORED);
            else throw new InvalidCharacterSheetException("Sem espaço para ativar ou guardar " + t.getName());
        }
        return finish(c, "TRAIN_TECHNIQUE", "training:" + t.getName(), before, t.getTrainingProgress());
    }

    @Transactional
    public SheetMartialResponse setState(UUID sheetId, UUID techniqueId, TechniqueStateRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        CharacterTechnique t = technique(c, techniqueId);
        TechniqueState target = req.state();
        if ((target != TechniqueState.ACTIVE && target != TechniqueState.STORED)
                || (t.getState() != TechniqueState.ACTIVE && t.getState() != TechniqueState.STORED)) {
            throw new InvalidCharacterSheetException("Só técnicas aprendidas alternam entre ativa e guardada");
        }
        if (t.getState() == target) return response(c.sheet());
        if (!hasRoom(techniqueRepository.findBySheetIdOrderByCreatedAt(sheetId), t, target)) {
            throw new InvalidCharacterSheetException(target == TechniqueState.ACTIVE
                    ? "Sem espaço ativo nesta graduação (máx. " + MasteryRules.MAX_ACTIVE_PER_GRADUATION + ")"
                    : "Sem espaço para guardar nesta graduação (máx. " + MasteryRules.MAX_STORED_PER_GRADUATION + ")");
        }
        TechniqueState before = t.getState();
        t.setState(target);
        return finish(c, "SET_TECHNIQUE_STATE", "technique:" + t.getName(), before, target);
    }

    @Transactional
    public SheetMartialResponse addMasteryPoints(UUID sheetId, UUID techniqueId, PointsRequest req, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterTechnique t = learned(technique(c, techniqueId));
        if (req.points() <= 0) throw new InvalidCharacterSheetException("Informe pontos de maestria positivos");
        Integer cap = MasteryRules.nextLevelCost(t.getTrainingPoints(), t.getMastery());
        if (cap == null) throw new InvalidCharacterSheetException("Este nível de maestria não acumula pontos");
        int before = t.getMasteryPoints();
        t.setMasteryPoints(Math.min(cap, before + req.points()));
        return finish(c, "ADJUST_MASTERY_POINTS", "mastery:" + t.getName(), before, t.getMasteryPoints());
    }

    @Transactional
    public SheetMartialResponse levelUp(UUID sheetId, UUID techniqueId, LevelUpRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        CharacterTechnique t = learned(technique(c, techniqueId));
        Mastery next = MasteryRules.nextLevel(t.getMastery());
        if (next == null) throw new InvalidCharacterSheetException("Este nível de maestria não sobe por pontos");
        int cost = MasteryRules.levelCost(t.getTrainingPoints(), next);
        if (t.getMasteryPoints() < cost) {
            throw new InvalidCharacterSheetException("Pontos de maestria insuficientes (" + t.getMasteryPoints()
                    + " de " + cost + ")");
        }
        if (!MasteryRules.bonusAllowed(t.getKind(), req.bonus(), t.getBonuses())) {
            throw new InvalidCharacterSheetException("Bonificação inválida para este item");
        }
        Mastery before = t.getMastery();
        t.setMasteryPoints(t.getMasteryPoints() - cost);
        t.setMastery(next);
        List<MasteryBonus> bonuses = new ArrayList<>(t.getBonuses());
        bonuses.add(req.bonus());
        if (next == Mastery.MASTERED) bonuses.add(MasteryBonus.APEX);
        t.setBonuses(bonuses);
        return finish(c, "LEVEL_UP", "mastery:" + t.getName(), before, next);
    }

    @Transactional
    public SheetMartialResponse setMastery(UUID sheetId, UUID techniqueId, MasteryRequest req, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterTechnique t = technique(c, techniqueId);
        Mastery before = t.getMastery();
        t.setMastery(req.mastery());
        t.setMasteryPoints(0);
        t.setBonuses(MasteryRules.bonusesFor(req.mastery(), t.getBonuses()));
        return finish(c, "SET_MASTERY", "mastery:" + t.getName(), before, req.mastery());
    }

    @Transactional
    public SheetMartialResponse removeTechnique(UUID sheetId, UUID techniqueId, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterTechnique t = technique(c, techniqueId);
        techniqueRepository.delete(t);
        return finish(c, "REMOVE_TECHNIQUE", "technique", t.getName(), null);
    }

    // ---------------------------------------------------------------- apoio

    private record Move(String name, Graduation graduation, int complexity, int trainingPoints) {}

    /** Snapshot do movimento: números do livro; sem eles, os calculados pela montagem. */
    private static Move findMove(MartialArt art, MoveKind kind, String name) {
        if (kind == MoveKind.TECHNIQUE) {
            for (ArtTechnique t : art.getTechniques()) {
                if (t.getName().equals(name)) {
                    var check = AssemblyRules.checkTechnique(art.getCategory(), t);
                    return new Move(t.getName(), grad(t.getGraduation()), or(t.getComplexity(), check.finalComplexity()),
                            or(t.getTrainingPoints(), check.trainingPoints()));
                }
            }
        } else {
            for (ArtBase b : art.getBases()) {
                if (b.getName().equals(name)) {
                    var check = AssemblyRules.checkBase(art.getCategory(), b);
                    return new Move(b.getName(), grad(b.getGraduation()), or(b.getComplexity(), check.finalComplexity()),
                            or(b.getTrainingPoints(), check.trainingPoints()));
                }
            }
        }
        throw new SheetEntryNotFoundException();
    }

    private static Graduation grad(Graduation g) {
        return g == null ? Graduation.GENERIC : g;
    }

    private static int or(Integer v, int fallback) {
        return v == null ? fallback : v;
    }

    /** Espaço livre para `target` na mesma arte e graduação (bases não têm limite). */
    private static boolean hasRoom(List<CharacterTechnique> all, CharacterTechnique t, TechniqueState target) {
        if (t.getKind() == MoveKind.BASE) return true;
        int max = target == TechniqueState.ACTIVE
                ? MasteryRules.MAX_ACTIVE_PER_GRADUATION : MasteryRules.MAX_STORED_PER_GRADUATION;
        long used = all.stream().filter(o -> !o.getId().equals(t.getId()) && o.getKind() == MoveKind.TECHNIQUE
                && o.getState() == target && o.getGraduation() == t.getGraduation()
                && o.getCharacterArt().getId().equals(t.getCharacterArt().getId())).count();
        return used < max;
    }

    private static CharacterTechnique learned(CharacterTechnique t) {
        if (t.getState() != TechniqueState.ACTIVE && t.getState() != TechniqueState.STORED) {
            throw new InvalidCharacterSheetException("A técnica ainda não foi aprendida");
        }
        return t;
    }

    private CharacterArt art(Ctx c, UUID id) {
        CharacterArt ca = characterArtRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        if (!ca.getSheet().getId().equals(c.sheet().getId())) throw new SheetEntryNotFoundException();
        return ca;
    }

    private CharacterTechnique technique(Ctx c, UUID id) {
        CharacterTechnique t = techniqueRepository.findById(id).orElseThrow(SheetEntryNotFoundException::new);
        if (!t.getSheet().getId().equals(c.sheet().getId())) throw new SheetEntryNotFoundException();
        return t;
    }

    /** Dono e Mestre; ficha de NPC: só o dono do NPC (age como Mestre), outros 404. */
    private Ctx load(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        if (sheet.isNpcSheet()) {
            if (!requesterId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
            return new Ctx(sheet, requesterId, null, true);
        }
        UUID campaignId = sheet.getCampaignMember().getCampaign().getId();
        CampaignMember m = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        boolean master = m.getRole() == Role.MASTER;
        boolean owner = sheet.getCampaignMember().getUser().getId().equals(requesterId);
        if (!master && !owner) throw new AccessDeniedException("Apenas o dono da ficha e o Mestre acessam as artes");
        return new Ctx(sheet, requesterId, m.getUser().getName(), master);
    }

    private Ctx master(UUID sheetId, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (!c.master()) throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
        return c;
    }

    private SheetMartialResponse finish(Ctx c, String action, String field, Object before, Object after) {
        activityLog.record(c.sheet(), c.actorId(), c.role(), ActivityCategory.PROGRESSION, action, field,
                before, after, null, null);
        CharacterSheet s = c.sheet();
        if (s.isNpcSheet()) {
            for (CampaignNpc cn : campaignNpcRepository.findByNpcId(s.getNpcId())) {
                events.publishEvent(new SheetUpdateMessage(SheetUpdateMessage.SheetEvent.SHEET_UPDATED, cn.getCampaign().getId(),
                        s.getId(), ActivityCategory.PROGRESSION, action, c.actorName()));
            }
        } else {
            events.publishEvent(new SheetUpdateMessage(SheetUpdateMessage.SheetEvent.SHEET_UPDATED,
                    s.getCampaignMember().getCampaign().getId(), s.getId(), ActivityCategory.PROGRESSION, action,
                    c.actorName()));
        }
        return response(s);
    }

    private SheetMartialResponse response(CharacterSheet sheet) {
        List<CharacterArtResponse> arts = characterArtRepository.findBySheetIdOrderByCreatedAt(sheet.getId()).stream()
                .map(a -> new CharacterArtResponse(a.getId(), a.getArtId(), a.getArtName(), a.getCategory(),
                        a.getGraduation(), a.getStatus())).toList();
        List<CharacterTechniqueResponse> techniques = techniqueRepository.findBySheetIdOrderByCreatedAt(sheet.getId())
                .stream().map(t -> new CharacterTechniqueResponse(t.getId(), t.getCharacterArt().getId(), t.getKind(),
                        t.getName(), t.getGraduation(), t.getComplexity(), t.getTrainingPoints(),
                        t.getTrainingProgress(), t.getState(), t.getMastery(), t.getMasteryPoints(),
                        MasteryRules.nextLevelCost(t.getTrainingPoints(), t.getMastery()), t.getBonuses())).toList();
        return new SheetMartialResponse(arts, techniques);
    }
}
