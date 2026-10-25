package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.FightRequest;
import com.tatakai.manager.dto.request.InstinctChoiceRequest;
import com.tatakai.manager.dto.request.OvercomeRequest;
import com.tatakai.manager.dto.request.ReviewArtRequest;
import com.tatakai.manager.dto.request.RivalRequest;
import com.tatakai.manager.dto.response.SheetProgressionResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.exception.SheetEntryNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterArtRepository;
import com.tatakai.manager.repository.CharacterInstinctRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.FightRecordRepository;
import com.tatakai.manager.repository.OvercomeDistributionRepository;
import com.tatakai.manager.repository.RivalRepository;
import com.tatakai.manager.repository.TimeSkipRepository;
import com.tatakai.manager.service.rules.ProgressionRules;
import com.tatakai.manager.service.rules.SheetRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Lutas (EL/PS), distribuição de PS e Instintos (F4b, PLANO §18). O Mestre registra e aprova; o jogador pede. */
@Service
public class ProgressionService {

    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final CharacterArtRepository characterArtRepository;
    private final FightRecordRepository fightRepository;
    private final OvercomeDistributionRepository overcomeRepository;
    private final CharacterInstinctRepository instinctRepository;
    private final TimeSkipRepository timeSkipRepository;
    private final RivalRepository rivalRepository;
    private final CatalogService catalogService;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher events;

    private record Ctx(CharacterSheet sheet, UUID actorId, String actorName, boolean master, boolean owner) {
        ActorRole role() {
            return master ? ActorRole.MASTER : ActorRole.PLAYER;
        }
    }

    public ProgressionService(CharacterSheetRepository sheetRepository, CampaignMemberRepository memberRepository,
                              CharacterArtRepository characterArtRepository, FightRecordRepository fightRepository,
                              OvercomeDistributionRepository overcomeRepository,
                              CharacterInstinctRepository instinctRepository, TimeSkipRepository timeSkipRepository,
                              RivalRepository rivalRepository, CatalogService catalogService, SheetActivityLogService activityLog,
                              ApplicationEventPublisher events) {
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.characterArtRepository = characterArtRepository;
        this.fightRepository = fightRepository;
        this.overcomeRepository = overcomeRepository;
        this.instinctRepository = instinctRepository;
        this.timeSkipRepository = timeSkipRepository;
        this.rivalRepository = rivalRepository;
        this.catalogService = catalogService;
        this.activityLog = activityLog;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public SheetProgressionResponse get(UUID sheetId, UUID requesterId) {
        return response(load(sheetId, requesterId).sheet());
    }

    // ---------------------------------------------------------------- lutas

    /** RN-PROG-07: o Mestre fecha a luta; EL entra na hora, PS ficam pendentes, EL por arte vira ponto de graduação. */
    @Transactional
    public List<SheetProgressionResponse> recordFight(UUID campaignId, FightRequest req, UUID requesterId) {
        CampaignMember gm = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        if (gm.getRole() != Role.MASTER) throw new AccessDeniedException("Apenas o Mestre registra lutas");
        Set<UUID> seen = new HashSet<>();
        if (!req.participants().stream().allMatch(p -> seen.add(p.sheetId()))) {
            throw new InvalidCharacterSheetException("Cada ficha entra uma vez por luta");
        }

        TimeSkip active = timeSkipRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                .filter(t -> t.getStatus() == TimeSkipStatus.ACTIVE).findFirst().orElse(null);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<String, FightMilestone> catalog = new HashMap<>();
        catalogService.milestonesFor(campaignId).forEach(m -> catalog.put(m.getName(), m));
        UUID fightId = UUID.randomUUID();

        List<SheetProgressionResponse> out = new ArrayList<>();
        for (FightRequest.Participant p : req.participants()) {
            CharacterSheet s = sheetRepository.findById(p.sheetId()).orElseThrow(CharacterSheetNotFoundException::new);
            if (s.isNpcSheet() || !s.getCampaignMember().getCampaign().getId().equals(campaignId)) {
                throw new CharacterSheetNotFoundException();
            }
            List<String> milestones = new ArrayList<>(new LinkedHashSet<>(p.milestones() == null ? List.of() : p.milestones()));
            List<FightMilestone> marked = new ArrayList<>();
            for (String m : milestones) {
                if (!catalog.containsKey(m)) throw new InvalidCharacterSheetException("Marco desconhecido: " + m);
                marked.add(catalog.get(m));
            }
            checkMilestones(marked, p.classification());

            boolean easyToday = p.classification() == FightClassification.EASY && (active != null
                    ? fightRepository.existsBySheetIdAndClassificationAndTimeSkipIdAndDayNumber(s.getId(),
                    FightClassification.EASY, active.getId(), active.getCurrentDay())
                    : fightRepository.existsBySheetIdAndClassificationAndTimeSkipIdIsNullAndFightDate(s.getId(),
                    FightClassification.EASY, today));
            int el = ProgressionRules.fightEl(p.classification(), easyToday, s.getSparringExperience(),
                    s.getFightExperience());
            int ps = ProgressionRules.fightPs(p.classification(), milestones.size(), s.getTal());
            Map<UUID, Integer> artEl = artEl(s, p.artEl(), el);
            if (marked.stream().anyMatch(FightMilestone::isGrantsArtPoint)) signature(s, p.artEl());

            int elBefore = s.getFightExperience();
            s.setFightExperience(elBefore + el);
            if (p.classification() == FightClassification.SPARRING) s.setSparringExperience(s.getSparringExperience() + el);
            s.setPendingOvercomePoints(s.getPendingOvercomePoints() + ps);
            if (ProgressionRules.crossesBarrier(p.classification())) {
                s.setBarrierOvercomePoints(s.getBarrierOvercomePoints() + ps);
            }
            fightRepository.save(FightRecord.builder().fightId(fightId).campaignId(campaignId).sheet(s)
                    .classification(p.classification()).elGained(el).psGained(ps).milestones(milestones).artEl(artEl)
                    .timeSkipId(active == null ? null : active.getId())
                    .dayNumber(active == null ? null : active.getCurrentDay())
                    .fightDate(today).note(req.note()).build());

            Ctx c = new Ctx(s, requesterId, gm.getUser().getName(), true, false);
            String reason = p.classification() + (milestones.isEmpty() ? "" : " · Marcos: " + String.join(", ", milestones));
            log(c, "FIGHT", "fightExperience", elBefore, s.getFightExperience(), reason);
            log(c, "FIGHT", "pendingOvercomePoints", s.getPendingOvercomePoints() - ps, s.getPendingOvercomePoints(), null);
            KeikenRank before = ProgressionRules.keiken(elBefore);
            KeikenRank after = ProgressionRules.keiken(s.getFightExperience());
            if (after != before) log(c, "KEIKEN_UP", "keiken", before, after, null);
            if (p.rival() != null) meetRival(c, p.rival());
            out.add(finish(c, "FIGHT"));
        }
        return out;
    }

    /** Regras dos Marcos que a ficha consegue conferir: só em Real/Letal e mínimo de outros de Desvantagem. */
    private static void checkMilestones(List<FightMilestone> marked, FightClassification c) {
        long disadvantages = marked.stream().filter(m -> m.getCategory() == MilestoneCategory.DISADVANTAGE).count();
        for (FightMilestone m : marked) {
            if (m.isSeriousOnly() && !ProgressionRules.crossesBarrier(c)) {
                throw new InvalidCharacterSheetException(m.getName() + " só vale em luta Real ou Letal");
            }
            Integer min = m.getMinOtherDisadvantage();
            long others = disadvantages - (m.getCategory() == MilestoneCategory.DISADVANTAGE ? 1 : 0);
            if (min != null && others < min) {
                throw new InvalidCharacterSheetException(m.getName() + " exige ao menos " + min
                        + " outros Marcos de Desvantagem");
            }
        }
    }

    /** Assinatura: +1 ponto de graduação (treino) à arte da técnica decisiva — a arte escolhida para o EL. */
    private void signature(CharacterSheet s, Map<UUID, Integer> requested) {
        UUID artId = requested == null ? null : requested.keySet().stream().findFirst().orElse(null);
        CharacterArt ca = artId == null ? null : characterArtRepository.findById(artId)
                .filter(a -> a.getSheet().getId().equals(s.getId()) && a.getStatus() == CharacterArtStatus.ACTIVE)
                .orElse(null);
        if (ca == null) throw new InvalidCharacterSheetException("Assinatura: escolha a arte da técnica decisiva");
        ca.setGraduationPoints(ca.getGraduationPoints() + 1);
    }

    /** EL distribuído entre artes ativas da ficha, sem duplicar: o pedido é cortado no EL que a luta rendeu. */
    private Map<UUID, Integer> artEl(CharacterSheet s, Map<UUID, Integer> requested, int el) {
        Map<UUID, Integer> out = new HashMap<>();
        if (requested == null) return out;
        int left = el;
        for (var e : requested.entrySet()) {
            int n = Math.min(left, e.getValue() == null ? 0 : e.getValue());
            if (n < 0) throw new InvalidCharacterSheetException("EL por arte inválido");
            if (n == 0) continue;
            CharacterArt ca = characterArtRepository.findById(e.getKey()).orElseThrow(SheetEntryNotFoundException::new);
            if (!ca.getSheet().getId().equals(s.getId()) || ca.getStatus() != CharacterArtStatus.ACTIVE) {
                throw new InvalidCharacterSheetException("A arte não está ativa nesta ficha");
            }
            left -= n;
            ca.setGraduationPoints(ca.getGraduationPoints() + n);
            out.put(ca.getId(), n);
        }
        return out;
    }

    /** Registro de Rivais: cria se não existir; +1 luta e +1 de Leitura (máx. 3). */
    private void meetRival(Ctx c, FightRequest.RivalRef ref) {
        String name = ref.name().trim();
        Rival r = rivalRepository.findBySheetIdAndName(c.sheet().getId(), name)
                .orElseGet(() -> Rival.builder().sheet(c.sheet()).name(name).build());
        if (ref.npcId() != null) r.setNpcId(ref.npcId());
        int before = r.getReadBonus();
        r.setFights(r.getFights() + 1);
        r.setReadBonus(Math.min(Rival.MAX_READ, before + 1));
        rivalRepository.save(r);
        log(c, "RIVAL", "rival:" + name, before, r.getReadBonus(), null);
    }

    @Transactional
    public SheetProgressionResponse updateRival(UUID sheetId, UUID rivalId, RivalRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        Rival r = rival(sheetId, rivalId);
        r.setArt(req.art());
        r.setKnownTechniques(req.knownTechniques() == null ? new ArrayList<>() : new ArrayList<>(req.knownTechniques()));
        log(c, "UPDATE_RIVAL", "rival:" + r.getName(), null, req.knownTechniques(), null);
        return finish(c, "UPDATE_RIVAL");
    }

    @Transactional
    public SheetProgressionResponse removeRival(UUID sheetId, UUID rivalId, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        Rival r = rival(sheetId, rivalId);
        rivalRepository.delete(r);
        log(c, "REMOVE_RIVAL", "rival", r.getName(), null, null);
        return finish(c, "REMOVE_RIVAL");
    }

    private Rival rival(UUID sheetId, UUID rivalId) {
        return rivalRepository.findById(rivalId).filter(r -> r.getSheet().getId().equals(sheetId))
                .orElseThrow(SheetEntryNotFoundException::new);
    }

    // ---------------------------------------------------------------- PS

    @Transactional
    public SheetProgressionResponse requestOvercome(UUID sheetId, OvercomeRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (overcomeRepository.existsBySheetIdAndStatus(sheetId, OvercomeStatus.PENDING)) {
            throw new InvalidCharacterSheetException("Já há uma distribuição aguardando o Mestre");
        }
        apply(c.sheet(), req.allocations(), true);
        overcomeRepository.save(OvercomeDistribution.builder().sheet(c.sheet())
                .allocations(new HashMap<>(req.allocations())).status(OvercomeStatus.PENDING).build());
        log(c, "REQUEST_OVERCOME", "pendingOvercomePoints", null, req.allocations(), null);
        return finish(c, "REQUEST_OVERCOME");
    }

    @Transactional
    public SheetProgressionResponse reviewOvercome(UUID sheetId, UUID distributionId, ReviewArtRequest req,
                                                   UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        OvercomeDistribution d = overcomeRepository.findById(distributionId)
                .filter(x -> x.getSheet().getId().equals(sheetId)).orElseThrow(SheetEntryNotFoundException::new);
        if (d.getStatus() != OvercomeStatus.PENDING) throw new InvalidCharacterSheetException("Distribuição já revisada");
        d.setReviewNote(req.reason());
        d.setReviewedAt(Instant.now());
        if (!Boolean.TRUE.equals(req.approved())) {
            d.setStatus(OvercomeStatus.REJECTED);
            log(c, "REJECT_OVERCOME", "pendingOvercomePoints", d.getAllocations(), null, req.reason());
            return finish(c, "REJECT_OVERCOME");
        }
        for (String line : apply(c.sheet(), d.getAllocations(), false)) {
            log(c, "APPROVE_OVERCOME", line, null, null, req.reason());
        }
        d.setStatus(OvercomeStatus.APPROVED);
        return finish(c, "APPROVE_OVERCOME");
    }

    /**
     * Aplica (ou só valida, com `dryRun`) uma distribuição: 1 PS = +1 atributo ou +3 no máximo de HP/SP; cruzar
     * Barreira consome PS de luta Real/Letal; além do Limitador só com todos os PS num alvo, e vale +1.
     */
    private List<String> apply(CharacterSheet s, Map<String, Integer> alloc, boolean dryRun) {
        int pending = s.getPendingOvercomePoints();
        String error = ProgressionRules.validateAllocation(alloc, pending);
        if (error != null) throw new InvalidCharacterSheetException(error);
        int total = alloc.values().stream().mapToInt(Integer::intValue).sum();
        boolean allIn = alloc.size() == 1 && total == pending;
        int barrier = s.getBarrierOvercomePoints();
        Map<String, Integer> limiters = limiters(s);
        Map<String, int[]> changes = new LinkedHashMap<>();

        for (var e : alloc.entrySet()) {
            String t = e.getKey();
            boolean status = ProgressionRules.STATUS_TARGETS.contains(t);
            int before = status ? ("HP".equals(t) ? s.getHpMax() : s.getSpMax()) : s.getAttribute(Attribute.valueOf(t));
            int v = before;
            int limiter = limiters.get(t);
            for (int k = 0; k < e.getValue(); k++) {
                if (v >= limiter) {
                    if (!allIn) {
                        throw new InvalidCharacterSheetException("Além do Limitador em " + t
                                + ": comprometa todos os PS pendentes neste único alvo");
                    }
                    v += 1;
                    break;
                }
                if (status) {
                    v = Math.min(limiter, v + ProgressionRules.STATUS_PER_PS);
                } else {
                    if (v == 3 || v == 6 || v == 9) {
                        if (barrier == 0) {
                            throw new InvalidCharacterSheetException("Cruzar a Barreira em " + t
                                    + " exige PS de luta Real ou Letal");
                        }
                        barrier--;
                    }
                    v++;
                }
            }
            changes.put(t, new int[]{before, v});
        }
        if (dryRun) return List.of();

        List<String> lines = new ArrayList<>();
        changes.forEach((t, bv) -> {
            switch (t) {
                case "HP" -> {
                    boolean full = s.getCurrentHp() >= SheetRules.effectiveHpMax(bv[0], s.getInjuries());
                    s.setHpMax(bv[1]);
                    if (full) s.setCurrentHp(s.getCurrentHp() + bv[1] - bv[0]);
                }
                case "SP" -> {
                    if (s.getCurrentSp() >= bv[0]) s.setCurrentSp(s.getCurrentSp() + bv[1] - bv[0]);
                    s.setSpMax(bv[1]);
                }
                default -> s.setAttribute(Attribute.valueOf(t), bv[1]);
            }
            lines.add(t + ": " + bv[0] + " → " + bv[1]);
        });
        s.setPendingOvercomePoints(pending - total);
        s.setBarrierOvercomePoints(Math.min(pending - total, barrier));
        return lines;
    }

    private static Map<String, Integer> limiters(CharacterSheet s) {
        Map<String, Integer> adj = s.getLimiterAdjustments() == null ? Map.of() : s.getLimiterAdjustments();
        Map<String, Integer> out = new HashMap<>();
        for (String t : ProgressionRules.ATTRIBUTE_TARGETS) {
            Attribute a = Attribute.valueOf(t);
            int injury = SheetRules.injuryLimiter(s.getInjuries(), a);
            out.put(t, SheetRules.attributeLimiter(a, s.getTal(), s.getBodyType(), injury, adj.getOrDefault(t, 0)));
        }
        out.put("HP", SheetRules.statusLimiter(ResourceType.HP, s.getTal(), s.getBodyType(), adj.getOrDefault("HP", 0) - SheetRules.injuryHp(s.getInjuries())));
        out.put("SP", SheetRules.statusLimiter(ResourceType.SP, s.getTal(), s.getBodyType(), adj.getOrDefault("SP", 0)));
        return out;
    }

    // ---------------------------------------------------------------- Instintos

    /** RN-PROG-08: 1 Instinto por patamar, da patente atual ou inferior. */
    @Transactional
    public SheetProgressionResponse chooseInstinct(UUID sheetId, InstinctChoiceRequest req, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        CharacterSheet s = c.sheet();
        List<CharacterInstinct> mine = instinctRepository.findBySheetIdOrderByCreatedAt(sheetId);
        int el = s.getFightExperience();
        if (mine.size() >= ProgressionRules.instinctSlots(el)) {
            throw new InvalidCharacterSheetException("Sem vaga de Instinto na patente atual");
        }
        UUID campaignId = s.isNpcSheet() ? null : s.getCampaignMember().getCampaign().getId();
        Instinct i = catalogService.instinctsFor(campaignId).stream().filter(x -> x.getId().equals(req.instinctId()))
                .findFirst().orElseThrow(SheetEntryNotFoundException::new);
        if (i.getRank().compareTo(ProgressionRules.keiken(el)) > 0) {
            throw new InvalidCharacterSheetException("Instinto acima da patente atual");
        }
        if (mine.stream().anyMatch(x -> x.getInstinctId().equals(i.getId()))) {
            throw new InvalidCharacterSheetException("A ficha já possui este Instinto");
        }
        instinctRepository.save(CharacterInstinct.builder().sheet(s).instinctId(i.getId()).name(i.getName())
                .rank(i.getRank()).description(i.getDescription()).build());
        log(c, "CHOOSE_INSTINCT", "instinct", null, i.getName(), null);
        return finish(c, "CHOOSE_INSTINCT");
    }

    @Transactional
    public SheetProgressionResponse removeInstinct(UUID sheetId, UUID characterInstinctId, UUID requesterId) {
        Ctx c = master(sheetId, requesterId);
        CharacterInstinct ci = instinctRepository.findById(characterInstinctId)
                .filter(x -> x.getSheet().getId().equals(sheetId)).orElseThrow(SheetEntryNotFoundException::new);
        instinctRepository.delete(ci);
        log(c, "REMOVE_INSTINCT", "instinct", ci.getName(), null, null);
        return finish(c, "REMOVE_INSTINCT");
    }

    // ---------------------------------------------------------------- apoio

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
        if (!master && !owner) throw new AccessDeniedException("Apenas o dono da ficha e o Mestre acessam a progressão");
        return new Ctx(sheet, requesterId, m.getUser().getName(), master, owner);
    }

    private Ctx master(UUID sheetId, UUID requesterId) {
        Ctx c = load(sheetId, requesterId);
        if (!c.master()) throw new AccessDeniedException("Apenas o Mestre pode realizar esta ação");
        return c;
    }

    private void log(Ctx c, String action, String field, Object before, Object after, String reason) {
        activityLog.record(c.sheet(), c.actorId(), c.role(), ActivityCategory.PROGRESSION, action, field,
                before, after, null, reason);
    }

    private SheetProgressionResponse finish(Ctx c, String action) {
        CharacterSheet s = sheetRepository.save(c.sheet());
        if (!s.isNpcSheet()) {
            events.publishEvent(new SheetUpdateMessage(SheetUpdateMessage.SheetEvent.SHEET_UPDATED,
                    s.getCampaignMember().getCampaign().getId(), s.getId(), ActivityCategory.PROGRESSION, action,
                    c.actorName()));
        }
        return response(s);
    }

    private SheetProgressionResponse response(CharacterSheet s) {
        return new SheetProgressionResponse(
                fightRepository.findBySheetIdOrderByCreatedAtDesc(s.getId()).stream()
                        .map(f -> new SheetProgressionResponse.Fight(f.getId(), f.getFightId(), f.getClassification(),
                                f.getElGained(), f.getPsGained(), f.getMilestones(), f.getArtEl(), f.getTimeSkipId(),
                                f.getDayNumber(), f.getFightDate(), f.getNote(), f.getCreatedAt())).toList(),
                overcomeRepository.findBySheetIdOrderByCreatedAtDesc(s.getId()).stream()
                        .map(d -> new SheetProgressionResponse.Overcome(d.getId(), d.getAllocations(), d.getStatus(),
                                d.getReviewNote(), d.getCreatedAt(), d.getReviewedAt())).toList(),
                instinctRepository.findBySheetIdOrderByCreatedAt(s.getId()).stream()
                        .map(i -> new SheetProgressionResponse.Instinct(i.getId(), i.getInstinctId(), i.getName(),
                                i.getRank(), i.getDescription())).toList(),
                rivalRepository.findBySheetIdOrderByName(s.getId()).stream()
                        .map(r -> new SheetProgressionResponse.RivalView(r.getId(), r.getName(), r.getNpcId(), r.getArt(),
                                r.getFights(), r.getReadBonus(), r.getKnownTechniques())).toList());
    }
}
