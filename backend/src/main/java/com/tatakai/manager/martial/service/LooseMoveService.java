package com.tatakai.manager.martial.service;

import com.tatakai.manager.campaign.entity.Role;
import com.tatakai.manager.campaign.repository.CampaignMemberRepository;
import com.tatakai.manager.catalog.entity.ArtCategory;
import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.entity.MoveKind;
import com.tatakai.manager.catalog.service.rules.AssemblyRules;
import com.tatakai.manager.martial.dto.request.LooseMoveRequest;
import com.tatakai.manager.martial.dto.response.LooseMoveResponse;
import com.tatakai.manager.martial.entity.LooseMove;
import com.tatakai.manager.martial.repository.LooseMoveRepository;
import com.tatakai.manager.shared.exception.AccessDeniedException;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;
import com.tatakai.manager.sheet.exception.SheetEntryNotFoundException;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Catálogo de técnicas e bases avulsas da campanha: o Mestre monta e decide a visibilidade para os jogadores. */
@Service
public class LooseMoveService {

    private final LooseMoveRepository repository;
    private final CampaignMemberRepository memberRepository;

    public LooseMoveService(LooseMoveRepository repository, CampaignMemberRepository memberRepository) {
        this.repository = repository;
        this.memberRepository = memberRepository;
    }

    public List<LooseMoveResponse> list(UUID campaignId, UUID userId) {
        boolean master = role(campaignId, userId) == Role.MASTER;
        return repository.findByCampaignIdOrderByNameAsc(campaignId).stream()
                .filter(m -> master || m.isVisible()).map(LooseMoveService::toResponse).toList();
    }

    public LooseMoveResponse create(UUID campaignId, LooseMoveRequest req, UUID userId) {
        requireMaster(campaignId, userId);
        boolean base = req.kind() == MoveKind.BASE;
        if (base ? req.base() == null : req.technique() == null) {
            throw new InvalidCharacterSheetException("Envie a montagem do tipo escolhido");
        }
        var check = base ? AssemblyRules.checkBase(ArtCategory.CREATED, req.base())
                : AssemblyRules.checkTechnique(ArtCategory.CREATED, req.technique());
        if (!check.issues().isEmpty()) throw new InvalidCharacterSheetException(String.join("; ", check.issues()));
        Graduation g = base ? req.base().getGraduation() : req.technique().getGraduation();
        String name = req.name().trim();
        if (base) req.base().setName(name); else req.technique().setName(name);
        LooseMove saved = repository.save(LooseMove.builder().id(UUID.randomUUID()).campaignId(campaignId).kind(req.kind())
                .name(name).graduation(g == null ? Graduation.GENERIC : g).complexity(check.finalComplexity())
                .trainingPoints(check.trainingPoints()).visible(Boolean.TRUE.equals(req.visible()))
                .technique(base ? null : req.technique()).base(base ? req.base() : null)
                .createdBy(userId).createdAt(Instant.now()).build());
        return toResponse(saved);
    }

    public LooseMoveResponse setVisible(UUID campaignId, UUID id, boolean visible, UUID userId) {
        requireMaster(campaignId, userId);
        LooseMove m = find(campaignId, id);
        m.setVisible(visible);
        return toResponse(repository.save(m));
    }

    public void delete(UUID campaignId, UUID id, UUID userId) {
        requireMaster(campaignId, userId);
        repository.delete(find(campaignId, id));
    }

    private LooseMove find(UUID campaignId, UUID id) {
        return repository.findById(id).filter(m -> campaignId.equals(m.getCampaignId()))
                .orElseThrow(SheetEntryNotFoundException::new);
    }

    private Role role(UUID campaignId, UUID userId) {
        return memberRepository.findByCampaignIdAndUserId(campaignId, userId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha")).getRole();
    }

    private void requireMaster(UUID campaignId, UUID userId) {
        if (role(campaignId, userId) != Role.MASTER) throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
    }

    private static LooseMoveResponse toResponse(LooseMove m) {
        return new LooseMoveResponse(m.getId(), m.getKind(), m.getName(), m.getGraduation(), m.getComplexity(),
                m.getTrainingPoints(), m.isVisible(), m.getTechnique(), m.getBase());
    }
}
