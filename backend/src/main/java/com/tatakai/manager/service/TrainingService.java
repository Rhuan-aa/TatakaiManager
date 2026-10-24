package com.tatakai.manager.service;

import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.dto.response.TrainingEntryResponse;
import com.tatakai.manager.entity.*;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.repository.BookingRepository;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterArtRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.CharacterTechniqueRepository;
import com.tatakai.manager.repository.TrainingEntryRepository;
import com.tatakai.manager.service.rules.MasteryRules;
import com.tatakai.manager.service.rules.SheetRules;
import com.tatakai.manager.service.rules.TrainingRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Treino e Repouso do TimeSkip (F4a, PLANO §17): processa os dias que passaram quando o Mestre avança o dia. */
@Service
public class TrainingService {

    private static final Set<String> RESOURCES = Set.of("HP", "SP", "KP");

    private final BookingRepository bookingRepository;
    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final TrainingEntryRepository entryRepository;
    private final CharacterTechniqueRepository techniqueRepository;
    private final CharacterArtRepository characterArtRepository;
    private final CharacterTechniqueService techniqueService;
    private final MartialNotebookService notebookService;
    private final RollService rollService;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher events;

    public TrainingService(BookingRepository bookingRepository, CharacterSheetRepository sheetRepository,
                           CampaignMemberRepository memberRepository, TrainingEntryRepository entryRepository,
                           CharacterTechniqueRepository techniqueRepository,
                           CharacterArtRepository characterArtRepository, CharacterTechniqueService techniqueService,
                           MartialNotebookService notebookService, RollService rollService, SheetActivityLogService activityLog,
                           ApplicationEventPublisher events) {
        this.bookingRepository = bookingRepository;
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.entryRepository = entryRepository;
        this.techniqueRepository = techniqueRepository;
        this.characterArtRepository = characterArtRepository;
        this.techniqueService = techniqueService;
        this.notebookService = notebookService;
        this.rollService = rollService;
        this.activityLog = activityLog;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TrainingEntryResponse> list(UUID sheetId, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        if (sheet.isNpcSheet()) {
            if (!requesterId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
        } else {
            CampaignMember m = memberRepository.findByCampaignIdAndUserId(
                            sheet.getCampaignMember().getCampaign().getId(), requesterId)
                    .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
            boolean owner = sheet.getCampaignMember().getUser().getId().equals(requesterId);
            if (m.getRole() != Role.MASTER && !owner) {
                throw new AccessDeniedException("Apenas o dono da ficha e o Mestre veem os treinos");
            }
        }
        return entryRepository.findBySheetIdOrderByCreatedAt(sheetId).stream()
                .map(e -> new TrainingEntryResponse(e.getId(), e.getTimeSkipId(), e.getDayNumber(), e.getTarget(),
                        e.getTechniqueId(), e.getGain(), e.isFull(), e.getStatus(), e.getNote(), e.getCreatedAt()))
                .toList();
    }

    /** Processa os dias ainda não processados anteriores a `newCurrentDay`. Chamar antes de mudar o currentDay. */
    public void processUpTo(TimeSkip ts, short newCurrentDay) {
        short processed = ts.getProcessedDay() != null ? ts.getProcessedDay() : (short) (ts.getCurrentDay() - 1);
        if (newCurrentDay - 1 <= processed) return;
        List<CharacterSheet> sheets = sheetRepository.findByCampaignMemberCampaignId(ts.getCampaign().getId()).stream()
                .filter(s -> s.getBuildStatus() == BuildStatus.COMPLETED).toList();
        List<Booking> bookings = bookingRepository.findByTimeSkipDay_TimeSkipId(ts.getId()).stream()
                .filter(b -> b.getNpcId() == null && (b.getSoloActivityType() == SoloActivityType.TREINO
                        || b.getSoloActivityType() == SoloActivityType.REPOUSO
                        || b.getSoloActivityType() == SoloActivityType.FORJA))
                .sorted(Comparator.comparingInt(Booking::getSlotNumber)).toList();
        for (short day = (short) (processed + 1); day < newCurrentDay; day++) {
            for (CharacterSheet sheet : sheets) {
                UUID userId = sheet.getCampaignMember().getUser().getId();
                short d = day;
                List<Booking> mine = bookings.stream()
                        .filter(b -> b.getUser().getId().equals(userId) && b.getTimeSkipDay().getDayNumber() == d)
                        .toList();
                processDay(ts, sheet, day, mine);
            }
        }
        ts.setProcessedDay((short) (newCurrentDay - 1));
    }

    private void processDay(TimeSkip ts, CharacterSheet sheet, short day, List<Booking> bookings) {
        boolean rested = false;
        for (Booking b : bookings) {
            if (b.getSoloActivityType() == SoloActivityType.TREINO && b.getTrainingTarget() != null) {
                train(ts, sheet, day, b);
            } else if (b.getSoloActivityType() == SoloActivityType.FORJA) {
                forge(ts, sheet, day, b);
            } else if (b.getSoloActivityType() == SoloActivityType.REPOUSO && b.getRestQuality() != null) {
                rest(sheet, b.getRestQuality());
                rested = true;
            }
        }
        if (!rested) {
            int before = sheet.getPhysicalFatigue();
            sheet.setPhysicalFatigue(before + 1);
            log(sheet, ActivityCategory.RESOURCE, "NO_REST", "physicalFatigue", before, before + 1,
                    "Dia " + day + " sem Repouso");
        }
        sheetRepository.save(sheet);
        events.publishEvent(new SheetUpdateMessage(SheetUpdateMessage.SheetEvent.SHEET_UPDATED,
                ts.getCampaign().getId(), sheet.getId(), ActivityCategory.PROGRESSION, "TRAINING_DAY", null));
    }

    private void train(TimeSkip ts, CharacterSheet sheet, short day, Booking b) {
        String target = b.getTrainingTarget();
        TrainingEntry.TrainingEntryBuilder entry = TrainingEntry.builder().sheet(sheet).timeSkipId(ts.getId())
                .bookingId(b.getId()).dayNumber(day).target(target).techniqueId(b.getTechniqueId());

        if (TrainingRules.TECHNIQUE.equals(target)) {
            CharacterTechnique t = b.getTechniqueId() == null ? null
                    : techniqueRepository.findById(b.getTechniqueId())
                    .filter(x -> x.getSheet().getId().equals(sheet.getId())).orElse(null);
            int gain = MasteryRules.trainingGain(sheet.getTal());
            String result = t == null ? null : techniqueService.applyTraining(t, gain);
            addFatigue(sheet, false, 1);
            entryRepository.save(entry.gain(result == null ? 0 : gain).full(true)
                    .status(result == null ? TrainingEntryStatus.LOST : TrainingEntryStatus.APPLIED)
                    .note(result == null ? "Técnica indisponível para treino" : (t.getName() + ": " + result)).build());
            log(sheet, ActivityCategory.PROGRESSION, "TRAINING", "technique", null, gain,
                    t == null ? null : t.getName());
            return;
        }

        if (TrainingRules.GRADUATION.equals(target)) {
            CharacterArt ca = b.getCharacterArtId() == null ? null
                    : characterArtRepository.findById(b.getCharacterArtId())
                    .filter(x -> x.getSheet().getId().equals(sheet.getId())).orElse(null);
            String result = ca == null ? null : techniqueService.applyGraduationTraining(ca);
            addFatigue(sheet, false, 1);
            entryRepository.save(entry.gain(result == null ? 0 : 1).full(true)
                    .status(result == null ? TrainingEntryStatus.LOST : TrainingEntryStatus.APPLIED)
                    .note(result == null ? "Arte indisponível para graduação" : (ca.getArtName() + ": " + result))
                    .build());
            log(sheet, ActivityCategory.PROGRESSION, "TRAINING", "graduation", null, result == null ? 0 : 1,
                    ca == null ? null : ca.getArtName());
            return;
        }

        List<TrainingEntry> sameTs = entryRepository.findBySheetIdAndTimeSkipId(sheet.getId(), ts.getId());
        int week = (day - 1) / 7;
        int nthToday = 1 + (int) sameTs.stream().filter(e -> e.getTarget().equals(target) && e.getDayNumber() == day).count();
        int fullsThisWeek = (int) sameTs.stream().filter(e -> e.getTarget().equals(target) && e.isFull()
                && e.getStatus() != TrainingEntryStatus.LOST && (e.getDayNumber() - 1) / 7 == week).count();
        int kpIndex = (int) entryRepository.countBySheetIdAndTarget(sheet.getId(), "KP") + 1;
        boolean mental = TrainingRules.isMental(target);
        FatigueBand band = SheetRules.fatigueBand(mental ? sheet.getMentalFatigue() : sheet.getPhysicalFatigue());

        TrainingRules.Outcome o = TrainingRules.train(target, sheet.getTal(), sheet.getBodyType(), nthToday,
                fullsThisWeek, kpIndex, band, ts.getVenue());
        addFatigue(sheet, mental, o.fatigue());
        entryRepository.save(entry.gain(o.gain()).full(o.full())
                .status(o.lost() ? TrainingEntryStatus.LOST : TrainingEntryStatus.PENDING).note(o.note()).build());
        log(sheet, ActivityCategory.PROGRESSION, "TRAINING", target, null, o.gain(), o.note());
    }

    /** Sessão de Forja (§9.3): pontos na Forja em andamento e +2 Desgaste Mental (treino + 1 extra). */
    private void forge(TimeSkip ts, CharacterSheet sheet, short day, Booking b) {
        String result = notebookService.applySession(sheet);
        addFatigue(sheet, true, 2);
        entryRepository.save(TrainingEntry.builder().sheet(sheet).timeSkipId(ts.getId()).bookingId(b.getId())
                .dayNumber(day).target("FORGE").gain(result == null ? 0 : MasteryRules.trainingGain(sheet.getTal()))
                .full(true).status(result == null ? TrainingEntryStatus.LOST : TrainingEntryStatus.APPLIED)
                .note(result == null ? "Nenhuma Forja em andamento" : result).build());
        log(sheet, ActivityCategory.PROGRESSION, "FORGE_SESSION", "forge", null, result, null);
    }

    private void addFatigue(CharacterSheet s, boolean mental, int amount) {
        if (mental) s.setMentalFatigue(s.getMentalFatigue() + amount);
        else s.setPhysicalFatigue(s.getPhysicalFatigue() + amount);
    }

    /** RN-PROG-06 + consolidação RN-PROG-04. */
    private void rest(CharacterSheet s, RestQuality q) {
        int die = TrainingRules.restDie(q);
        int conQ = SheetRules.modQ(s.getCon());
        int hp = rollService.rollInternal(s, "2d" + die, "Repouso: HP", null, ActorRole.SYSTEM).getTotal() + conQ;
        int sp = rollService.rollInternal(s, "4d" + die, "Repouso: SP", null, ActorRole.SYSTEM).getTotal() + conQ;
        int effMax = SheetRules.effectiveHpMax(s.getHpMax(), s.getInjuries());
        s.setCurrentHp(Math.min(effMax, s.getCurrentHp() + hp));
        s.setCurrentSp(Math.min(s.getSpMax(), s.getCurrentSp() + sp));
        s.setCurrentKp(s.getKpMax());
        int rec = TrainingRules.restFatigueRecovery(q);
        s.setPhysicalFatigue(Math.max(0, s.getPhysicalFatigue() - rec));
        s.setMentalFatigue(Math.max(0, s.getMentalFatigue() - rec));
        log(s, ActivityCategory.RESOURCE, "REST", "rest", null, q.name(), "+" + hp + " HP, +" + sp + " SP");

        for (TrainingEntry e : entryRepository.findBySheetIdOrderByCreatedAt(s.getId())) {
            if (e.getStatus() != TrainingEntryStatus.PENDING) continue;
            int gain = q == RestQuality.PRECARIOUS ? TrainingRules.precarious(e.getGain()) : e.getGain();
            e.setStatus(TrainingEntryStatus.CONSOLIDATED);
            if (gain <= 0) continue;
            int applied = RESOURCES.contains(e.getTarget()) ? consolidateResource(s, e.getTarget(), gain)
                    : consolidateAttribute(s, Attribute.valueOf(e.getTarget()), gain);
            e.setNote((e.getNote() == null ? "" : e.getNote() + " · ") + "consolidado +" + applied);
        }
    }

    private int consolidateAttribute(CharacterSheet s, Attribute a, int gain) {
        int before = s.getAttribute(a);
        int capped = TrainingRules.barrierCap(before, gain);
        int injury = s.getInjuries().stream().filter(i -> !i.isTreated() && i.getAffectedAttribute() == a)
                .mapToInt(PermanentInjury::getLimiterReduction).sum();
        int limiter = SheetRules.attributeLimiter(a, s.getTal(), s.getBodyType(), injury,
                adjustments(s).getOrDefault(a.name(), 0));
        int after = before + beyondLimiter(s, a.name(), before, capped, limiter);
        s.setAttribute(a, after);
        log(s, ActivityCategory.PROGRESSION, "CONSOLIDATE", a.name(), before, after, null);
        return after - before;
    }

    private int consolidateResource(CharacterSheet s, String key, int gain) {
        ResourceType r = ResourceType.valueOf(key);
        int limiter = SheetRules.statusLimiter(r, s.getTal(), s.getBodyType(), adjustments(s).getOrDefault(key, 0));
        int before = switch (r) {
            case HP -> s.getHpMax();
            case SP -> s.getSpMax();
            case KP -> s.getKpMax();
        };
        int after = before + beyondLimiter(s, key, before, gain, limiter);
        int added = after - before;
        // RN-REC-02: o atual sobe junto só se estava cheio
        switch (r) {
            case HP -> {
                boolean full = s.getCurrentHp() >= SheetRules.effectiveHpMax(before, s.getInjuries());
                s.setHpMax(after);
                if (full) s.setCurrentHp(s.getCurrentHp() + added);
            }
            case SP -> {
                if (s.getCurrentSp() >= before) s.setCurrentSp(s.getCurrentSp() + added);
                s.setSpMax(after);
            }
            case KP -> {
                if (s.getCurrentKp() >= before) s.setCurrentKp(s.getCurrentKp() + added);
                s.setKpMax(after);
            }
        }
        log(s, ActivityCategory.PROGRESSION, "CONSOLIDATE", key + "max", before, after, null);
        return added;
    }

    /** Até o Limitador entra inteiro; além dele cada treino vale meio ponto (2 treinos = 1), guardado em limiterCarry. */
    private static int beyondLimiter(CharacterSheet s, String key, int value, int gain, int limiter) {
        int within = Math.max(0, Math.min(gain, limiter - value));
        if (within == gain) return gain;
        Map<String, Integer> carry = s.getLimiterCarry() == null ? new HashMap<>() : new HashMap<>(s.getLimiterCarry());
        int halves = carry.getOrDefault(key, 0) + 1;
        carry.put(key, halves % 2);
        s.setLimiterCarry(carry);
        return within + halves / 2;
    }

    private static Map<String, Integer> adjustments(CharacterSheet s) {
        return s.getLimiterAdjustments() == null ? Map.of() : s.getLimiterAdjustments();
    }

    private void log(CharacterSheet s, ActivityCategory cat, String action, String field, Object before, Object after,
                     String reason) {
        activityLog.record(s, null, ActorRole.SYSTEM, cat, action, field, before, after, null, reason);
    }
}
