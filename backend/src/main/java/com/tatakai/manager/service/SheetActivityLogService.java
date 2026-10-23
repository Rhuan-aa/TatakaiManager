package com.tatakai.manager.service;

import com.tatakai.manager.dto.response.SheetActivityLogResponse;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.SheetActivityLog;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.SheetActivityLogRepository;
import com.tatakai.manager.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SheetActivityLogService {

    private final SheetActivityLogRepository logRepository;
    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;

    public SheetActivityLogService(SheetActivityLogRepository logRepository,
                                   CharacterSheetRepository sheetRepository,
                                   CampaignMemberRepository memberRepository,
                                   UserRepository userRepository) {
        this.logRepository = logRepository;
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /** Grava uma entrada imutável; participa da transação do chamador. */
    @Transactional
    public void record(CharacterSheet sheet, UUID actorId, ActorRole role, ActivityCategory category,
                       String action, String field, Object before, Object after,
                       UUID rollId, String reason) {
        logRepository.save(SheetActivityLog.builder()
                .sheetId(sheet.getId())
                .campaignId(sheet.isNpcSheet() ? null : sheet.getCampaignMember().getCampaign().getId())
                .actorId(actorId)
                .actorRole(role)
                .category(category)
                .action(action)
                .field(field)
                .beforeValue(before == null ? null : String.valueOf(before))
                .afterValue(after == null ? null : String.valueOf(after))
                .rollId(rollId)
                .reason(reason)
                .build());
    }

    @Transactional(readOnly = true)
    public List<SheetActivityLogResponse> listForSheet(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId)
                .orElseThrow(CharacterSheetNotFoundException::new);
        if (sheet.isNpcSheet()) {
            if (!requesterId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
            return toResponses(logRepository.findBySheetIdOrderByCreatedAtDesc(sheetId));
        }
        CampaignMember owner = sheet.getCampaignMember();
        CampaignMember requester = requireMembership(owner.getCampaign().getId(), requesterId);
        boolean isOwner = owner.getUser().getId().equals(requesterId);
        if (requester.getRole() != Role.MASTER && !isOwner) {
            throw new AccessDeniedException("Você não pode ver o histórico desta ficha");
        }
        return toResponses(logRepository.findBySheetIdOrderByCreatedAtDesc(sheetId));
    }

    @Transactional(readOnly = true)
    public List<SheetActivityLogResponse> listForCampaign(UUID campaignId, UUID sheetId,
                                                          ActivityCategory category, Instant from,
                                                          Instant to, UUID requesterId) {
        requireCampaignMaster(campaignId, requesterId);
        List<SheetActivityLog> filtered = logRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId)
                .stream()
                .filter(l -> sheetId == null || sheetId.equals(l.getSheetId()))
                .filter(l -> category == null || category == l.getCategory())
                .filter(l -> from == null || !l.getCreatedAt().isBefore(from))
                .filter(l -> to == null || !l.getCreatedAt().isAfter(to))
                .toList();
        return toResponses(filtered);
    }

    private List<SheetActivityLogResponse> toResponses(List<SheetActivityLog> logs) {
        List<UUID> actorIds = logs.stream().map(SheetActivityLog::getActorId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, String> names = userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        return logs.stream().map(l -> new SheetActivityLogResponse(
                l.getId(), l.getSheetId(), l.getActorId(),
                l.getActorId() == null ? null : names.get(l.getActorId()),
                l.getActorRole(), l.getCategory(), l.getAction(), l.getField(),
                l.getBeforeValue(), l.getAfterValue(), l.getRollId(), l.getReason(),
                l.getCreatedAt())).toList();
    }

    private CampaignMember requireMembership(UUID campaignId, UUID userId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
    }

    private void requireCampaignMaster(UUID campaignId, UUID userId) {
        if (requireMembership(campaignId, userId).getRole() != Role.MASTER) {
            throw new AccessDeniedException("Apenas o Mestre pode ver o histórico da campanha");
        }
    }
}
