package com.tatakai.manager.sheet.service;

import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.dto.request.CreateChangeRequest;
import com.tatakai.manager.sheet.dto.request.GmAdjustmentRequest;
import com.tatakai.manager.sheet.dto.request.ReviewChangeRequest;
import com.tatakai.manager.sheet.dto.response.ChangeRequestResponse;
import com.tatakai.manager.sheet.dto.response.SheetUpdateMessage;
import com.tatakai.manager.sheet.dto.response.SheetUpdateMessage.SheetEvent;
import com.tatakai.manager.sheet.entity.ActivityCategory;
import com.tatakai.manager.sheet.entity.ActorRole;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.SheetChangeRequest;
import com.tatakai.manager.sheet.entity.SheetChangeStatus;
import com.tatakai.manager.sheet.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.repository.SheetChangeRequestRepository;
import com.tatakai.manager.sheet.service.rules.ChangeSummary;

import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Exclusão de ficha de jogador e pacotes de ajuste proposto pelo dono (aprovados pelo Mestre). */
@Service
public class SheetManagementService {

    /** Tabelas que apontam a ficha por {@code sheet_id}, na ordem segura de exclusão (técnica antes da arte). */
    private static final List<String> SHEET_TABLES = List.of(
            "character_techniques", "character_arts", "notebook_drafts", "forges", "character_instincts",
            "fight_records", "training_entries", "rivals", "overcome_distributions", "sheet_images",
            "sheet_activity_logs", "sheet_change_requests", "rolls", "character_builds");

    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final SheetChangeRequestRepository changeRepository;
    private final CharacterSheetService sheetService;
    private final ApplicationEventPublisher events;
    private final EntityManager em;
    private final SheetActivityLogService activityLog;

    public SheetManagementService(CharacterSheetRepository sheetRepository, CampaignMemberRepository memberRepository,
                                  SheetChangeRequestRepository changeRepository, CharacterSheetService sheetService,
                                  ApplicationEventPublisher events, EntityManager em, SheetActivityLogService activityLog) {
        this.activityLog = activityLog;
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.changeRepository = changeRepository;
        this.sheetService = sheetService;
        this.events = events;
        this.em = em;
    }

    @Transactional
    public void delete(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = playerSheet(sheetId);
        access(sheet, requesterId);
        Number inCombat = (Number) em.createNativeQuery(
                "select count(*) from encounter_participants where sheet_id = :id")
                .setParameter("id", sheetId).getSingleResult();
        if (inCombat.longValue() > 0) {
            throw new InvalidCharacterSheetException("A ficha participa de um combate e não pode ser excluída");
        }
        for (String table : SHEET_TABLES) {
            em.createNativeQuery("delete from " + table + " where sheet_id = :id").setParameter("id", sheetId).executeUpdate();
        }
        UUID campaignId = campaignId(sheet);
        sheetRepository.delete(sheet);
        sheetRepository.flush();
        events.publishEvent(new SheetUpdateMessage(SheetEvent.SHEET_UPDATED, campaignId, sheetId,
                ActivityCategory.GM_ADJUSTMENT, "DELETE_SHEET", null));
    }

    @Transactional
    public ChangeRequestResponse propose(UUID sheetId, CreateChangeRequest req, UUID requesterId) {
        CharacterSheet sheet = playerSheet(sheetId);
        access(sheet, requesterId);
        if (!sheet.getCampaignMember().getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Apenas o dono da ficha pode propor ajustes");
        }
        if (sheet.getBuildStatus() != BuildStatus.COMPLETED) {
            throw new InvalidCharacterSheetException("Só uma ficha pronta recebe pedidos de ajuste");
        }
        if (changeRepository.existsBySheetIdAndStatus(sheetId, SheetChangeStatus.PENDING)) {
            throw new InvalidCharacterSheetException("Já existe um pedido de ajuste aguardando o Mestre");
        }
        SheetChangeRequest saved = changeRepository.save(SheetChangeRequest.builder()
                .sheetId(sheetId).requesterId(requesterId).status(SheetChangeStatus.PENDING)
                .changes(req.changes()).build());
        activityLog.record(sheet, requesterId, ActorRole.PLAYER, ActivityCategory.GM_ADJUSTMENT, "PROPOSE_CHANGES",
                null, null, String.join("; ", ChangeSummary.describeAll(sheet, req.changes())), null, null);
        publish(sheet, "PROPOSE_CHANGES");
        return toResponse(saved, sheet);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequestResponse> list(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = playerSheet(sheetId);
        access(sheet, requesterId);
        return changeRepository.findBySheetIdOrderByCreatedAtDesc(sheetId).stream().map(r -> toResponse(r, sheet)).toList();
    }

    /** Aprovar aplica todos os ajustes como Mestre, na mesma transação: se um falhar, nenhum vale. */
    @Transactional
    public ChangeRequestResponse review(UUID sheetId, UUID requestId, ReviewChangeRequest req, UUID requesterId) {
        CharacterSheet sheet = playerSheet(sheetId);
        if (access(sheet, requesterId) != Role.MASTER) {
            throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
        }
        SheetChangeRequest cr = pending(sheetId, requestId);
        // resumo antes de aplicar: guarda os valores de antes de cada alteração
        List<String> lines = ChangeSummary.describeAll(sheet, cr.getChanges());
        String summary = String.join("; ", lines);
        cr.setSummary(String.join("\n", lines));
        if (req.approved()) {
            for (GmAdjustmentRequest change : cr.getChanges()) {
                sheetService.applyGmAdjustment(sheetId, change, requesterId);
            }
        }
        cr.setStatus(req.approved() ? SheetChangeStatus.APPROVED : SheetChangeStatus.REJECTED);
        cr.setReviewNote(req.note());
        cr.setReviewedAt(Instant.now());
        String action = req.approved() ? "APPROVE_CHANGES" : "REJECT_CHANGES";
        activityLog.record(sheet, requesterId, ActorRole.MASTER, ActivityCategory.GM_ADJUSTMENT, action,
                null, null, summary, null, req.note());
        publish(sheet, action);
        return toResponse(changeRepository.save(cr), sheet);
    }

    @Transactional
    public void cancel(UUID sheetId, UUID requestId, UUID requesterId) {
        CharacterSheet sheet = playerSheet(sheetId);
        access(sheet, requesterId);
        SheetChangeRequest cr = pending(sheetId, requestId);
        if (!cr.getRequesterId().equals(requesterId)) {
            throw new AccessDeniedException("Apenas quem pediu pode cancelar");
        }
        changeRepository.delete(cr);
        publish(sheet, "CANCEL_CHANGES");
    }

    private SheetChangeRequest pending(UUID sheetId, UUID requestId) {
        SheetChangeRequest cr = changeRepository.findById(requestId)
                .filter(r -> r.getSheetId().equals(sheetId)).orElseThrow(CharacterSheetNotFoundException::new);
        if (cr.getStatus() != SheetChangeStatus.PENDING) {
            throw new InvalidCharacterSheetException("O pedido já foi decidido");
        }
        return cr;
    }

    private CharacterSheet playerSheet(UUID sheetId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        if (sheet.isNpcSheet()) throw new InvalidCharacterSheetException("Operação válida só para ficha de jogador");
        return sheet;
    }

    /** Mestre da campanha ou dono da ficha; devolve o papel de quem pede. */
    private Role access(CharacterSheet sheet, UUID requesterId) {
        CampaignMember m = memberRepository.findByCampaignIdAndUserId(campaignId(sheet), requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        boolean owner = sheet.getCampaignMember().getUser().getId().equals(requesterId);
        if (m.getRole() != Role.MASTER && !owner) {
            throw new AccessDeniedException("Apenas o Mestre ou o dono da ficha pode realizar esta ação");
        }
        return m.getRole();
    }

    private UUID campaignId(CharacterSheet sheet) {
        return sheet.getCampaignMember().getCampaign().getId();
    }

    private void publish(CharacterSheet sheet, String action) {
        events.publishEvent(new SheetUpdateMessage(SheetEvent.SHEET_UPDATED, campaignId(sheet), sheet.getId(),
                ActivityCategory.GM_ADJUSTMENT, action, null));
    }

    /** Resumo calculado contra a ficha atual (pedido pendente mostra "antes → depois"). */
    private ChangeRequestResponse toResponse(SheetChangeRequest r, CharacterSheet sheet) {
        return new ChangeRequestResponse(r.getId(), r.getSheetId(), r.getRequesterId(), r.getStatus(), r.getChanges(),
                r.getSummary() != null ? List.of(r.getSummary().split("\n")) : ChangeSummary.describeAll(sheet, r.getChanges()),
                r.getReviewNote(), r.getCreatedAt(), r.getReviewedAt());
    }
}
