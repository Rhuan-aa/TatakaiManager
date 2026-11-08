package com.tatakai.manager.approval.service;

import com.tatakai.manager.approval.dto.response.ApprovalItemResponse;
import com.tatakai.manager.auth.entity.User;
import com.tatakai.manager.build.entity.BuildStatus;
import com.tatakai.manager.build.entity.TraitSlot;
import com.tatakai.manager.build.entity.TraitSlotStatus;
import com.tatakai.manager.build.repository.CharacterBuildRepository;
import com.tatakai.manager.campaign.entity.CampaignMember;
import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.exception.CampaignNotFoundException;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.campaign.repository.CampaignRepository;
import com.tatakai.manager.catalog.entity.ArtStatus;
import com.tatakai.manager.catalog.repository.MartialArtRepository;
import com.tatakai.manager.log.entity.LogStatus;
import com.tatakai.manager.log.repository.InteractionLogRepository;
import com.tatakai.manager.martial.entity.CharacterArtStatus;
import com.tatakai.manager.martial.entity.NotebookStatus;
import com.tatakai.manager.martial.entity.TechniqueState;
import com.tatakai.manager.martial.repository.CharacterArtRepository;
import com.tatakai.manager.martial.repository.CharacterTechniqueRepository;
import com.tatakai.manager.martial.repository.NotebookDraftRepository;
import com.tatakai.manager.progression.entity.OvercomeStatus;
import com.tatakai.manager.progression.repository.OvercomeDistributionRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.timeskip.entity.SubmissionStatus;
import com.tatakai.manager.timeskip.entity.TimeSkip;
import com.tatakai.manager.timeskip.entity.TimeSkipStatus;
import com.tatakai.manager.timeskip.repository.TimeSkipRepository;
import com.tatakai.manager.timeskip.repository.TimeSkipSubmissionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fila única de pendências do Mestre (fichas, artes, técnicas, rascunhos, PS, logs e envios de TimeSkip). */
@Service
@Transactional(readOnly = true)
public class ApprovalService {

    private static final int TITLE_MAX = 60;

    private final CampaignRepository campaignRepository;
    private final CampaignMemberRepository memberRepository;
    private final CharacterSheetRepository sheetRepository;
    private final CharacterBuildRepository buildRepository;
    private final CharacterArtRepository artRepository;
    private final CharacterTechniqueRepository techniqueRepository;
    private final NotebookDraftRepository draftRepository;
    private final OvercomeDistributionRepository overcomeRepository;
    private final MartialArtRepository martialArtRepository;
    private final InteractionLogRepository logRepository;
    private final TimeSkipRepository timeSkipRepository;
    private final TimeSkipSubmissionRepository submissionRepository;

    public ApprovalService(CampaignRepository campaignRepository,
                           CampaignMemberRepository memberRepository,
                           CharacterSheetRepository sheetRepository,
                           CharacterBuildRepository buildRepository,
                           CharacterArtRepository artRepository,
                           CharacterTechniqueRepository techniqueRepository,
                           NotebookDraftRepository draftRepository,
                           OvercomeDistributionRepository overcomeRepository,
                           MartialArtRepository martialArtRepository,
                           InteractionLogRepository logRepository,
                           TimeSkipRepository timeSkipRepository,
                           TimeSkipSubmissionRepository submissionRepository) {
        this.campaignRepository = campaignRepository;
        this.memberRepository = memberRepository;
        this.sheetRepository = sheetRepository;
        this.buildRepository = buildRepository;
        this.artRepository = artRepository;
        this.techniqueRepository = techniqueRepository;
        this.draftRepository = draftRepository;
        this.overcomeRepository = overcomeRepository;
        this.martialArtRepository = martialArtRepository;
        this.logRepository = logRepository;
        this.timeSkipRepository = timeSkipRepository;
        this.submissionRepository = submissionRepository;
    }

    public List<ApprovalItemResponse> list(UUID campaignId, UUID requesterId) {
        if (!campaignRepository.existsById(campaignId)) throw new CampaignNotFoundException();
        if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode ver as pendências");
        }

        List<ApprovalItemResponse> items = new ArrayList<>();
        Map<UUID, String> names = new HashMap<>();
        for (CampaignMember m : memberRepository.findByCampaignId(campaignId)) {
            names.put(m.getUser().getId(), m.getUser().getName());
        }

        // ponytail: várias consultas por ficha (N+1); mesa pequena, trocar por findBySheetIdInAndStatus se crescer
        for (CharacterSheet sheet : sheetRepository.findByCampaignMemberCampaignId(campaignId)) {
            sheetItems(sheet, items);
        }
        martialArtRepository.findByCampaignId(campaignId).stream()
                .filter(a -> a.getStatus() == ArtStatus.PENDING_APPROVAL)
                .forEach(a -> items.add(new ApprovalItemResponse("CREATED_ART", a.getId(), a.getName(),
                        names.get(a.getAuthorId()), a.getUpdatedAt(), null, null, a.getId(), null, a.getAuthorId())));
        logRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .filter(l -> l.getStatus() == LogStatus.PENDING)
                .forEach(l -> items.add(new ApprovalItemResponse("LOG", l.getId(), truncate(l.getNarrative()),
                        l.getAuthor().getName(), l.getCreatedAt(), null, null, null, l.getId(),
                        l.getAuthor().getId())));

        Map<UUID, TimeSkip> active = new HashMap<>();
        timeSkipRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .filter(t -> t.getStatus() == TimeSkipStatus.ACTIVE)
                .forEach(t -> active.put(t.getId(), t));
        if (!active.isEmpty()) {
            submissionRepository.findByTimeSkipIdInAndStatus(active.keySet(), SubmissionStatus.PENDING)
                    .forEach(s -> items.add(new ApprovalItemResponse("TIMESKIP_SUBMISSION", s.getId(),
                            "Agendamentos de " + active.get(s.getTimeSkipId()).getName(),
                            names.get(s.getUserId()), s.getSubmittedAt(), null, s.getTimeSkipId(), null, null,
                            s.getUserId())));
        }

        items.sort(Comparator.comparing(ApprovalItemResponse::createdAt,
                Comparator.nullsLast(Comparator.<Instant>naturalOrder())));
        return items;
    }

    private void sheetItems(CharacterSheet sheet, List<ApprovalItemResponse> items) {
        UUID sid = sheet.getId();
        User owner = sheet.getCampaignMember().getUser();
        String who = owner.getName();
        UUID uid = owner.getId();
        String sheetName = sheet.getName() == null || sheet.getName().isBlank() ? who : sheet.getName();

        if (sheet.getBuildStatus() == BuildStatus.PENDING_APPROVAL) {
            items.add(sheetItem("SHEET", sid, sheetName, who, sheet.getUpdatedAt(), sid, uid));
        }
        buildRepository.findById(sid).ifPresent(b -> {
            if (b.getState() == null || b.getState().getTraitSlots() == null) return;
            for (TraitSlot slot : b.getState().getTraitSlots()) {
                if (slot.getStatus() != TraitSlotStatus.PENDING_GM) continue;
                String nome = slot.getTraitName() != null ? slot.getTraitName() : "slot " + (slot.getIndex() + 1);
                items.add(sheetItem("BUILD_TRAIT", sid, "Traço: " + nome, who, b.getUpdatedAt(), sid, uid));
            }
        });
        artRepository.findBySheetIdOrderByCreatedAt(sid).stream()
                .filter(a -> a.getStatus() == CharacterArtStatus.REQUESTED)
                .forEach(a -> items.add(new ApprovalItemResponse("ART_REQUEST", a.getId(),
                        "Arte: " + a.getArtName(), who, a.getCreatedAt(), sid, null, a.getArtId(), null, uid)));
        techniqueRepository.findBySheetIdOrderByCreatedAt(sid).stream()
                .filter(t -> t.getState() == TechniqueState.REQUESTED)
                .forEach(t -> items.add(sheetItem("TECHNIQUE_REQUEST", t.getId(), t.getName(), who,
                        t.getCreatedAt(), sid, uid)));
        draftRepository.findBySheetIdOrderByCreatedAt(sid).stream()
                .filter(d -> d.getStatus() == NotebookStatus.PENDING_APPROVAL)
                .forEach(d -> items.add(sheetItem("NOTEBOOK_DRAFT", d.getId(), d.getName(), who,
                        d.getCreatedAt(), sid, uid)));
        overcomeRepository.findBySheetIdOrderByCreatedAtDesc(sid).stream()
                .filter(o -> o.getStatus() == OvercomeStatus.PENDING)
                .forEach(o -> items.add(sheetItem("OVERCOME", o.getId(), "Distribuição de PS", who,
                        o.getCreatedAt(), sid, uid)));
    }

    private static ApprovalItemResponse sheetItem(String kind, UUID id, String title, String who,
                                                  Instant at, UUID sheetId, UUID userId) {
        return new ApprovalItemResponse(kind, id, title, who, at, sheetId, null, null, null, userId);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() <= TITLE_MAX ? s : s.substring(0, TITLE_MAX);
    }
}
