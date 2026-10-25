package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.CampaignRollRequest;
import com.tatakai.manager.dto.request.RollOverride;
import com.tatakai.manager.dto.request.RollRequest;
import com.tatakai.manager.dto.request.RollTestRequest;
import com.tatakai.manager.dto.response.RollMessage;
import com.tatakai.manager.dto.response.RollProfileResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.Roll;
import com.tatakai.manager.entity.RollChannel;
import com.tatakai.manager.entity.RollMode;
import com.tatakai.manager.entity.RollPart;
import com.tatakai.manager.entity.RollSourceType;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidRollException;
import com.tatakai.manager.exception.RollNotFoundException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.CustomRollChannelRepository;
import com.tatakai.manager.repository.RollRepository;
import com.tatakai.manager.repository.UserRepository;
import com.tatakai.manager.service.rules.RollRules;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RollService {

    private static final Pattern EXPRESSION = Pattern.compile("^(\\d{1,2})d(\\d{1,3})([+-]\\d{1,3})?$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int HISTORY_SIZE = 100;

    public record DiceExpression(int count, int sides, int modifier) {}

    private final RollRepository rollRepository;
    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher events;
    private final RollProfileService profiles;
    private final CustomRollChannelRepository customChannels;

    public RollService(RollRepository rollRepository,
                       CharacterSheetRepository sheetRepository,
                       CampaignMemberRepository memberRepository,
                       UserRepository userRepository,
                       SheetActivityLogService activityLog,
                       ApplicationEventPublisher events,
                       RollProfileService profiles,
                       CustomRollChannelRepository customChannels) {
        this.rollRepository = rollRepository;
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
        this.events = events;
        this.profiles = profiles;
        this.customChannels = customChannels;
    }

    /** Quem está rolando e em qual campanha o resultado vale (null = só log da ficha). */
    private record Access(CharacterSheet sheet, UUID campaignId, boolean master, User actor) {}

    private Access access(UUID sheetId, UUID requesterId, UUID npcCampaignId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId).orElseThrow(CharacterSheetNotFoundException::new);
        if (sheet.isNpcSheet()) {
            if (!requesterId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
            User actor = userRepository.findById(requesterId).orElseThrow(CharacterSheetNotFoundException::new);
            if (npcCampaignId != null && !memberRepository.existsByCampaignIdAndUserIdAndRole(
                    npcCampaignId, requesterId, Role.MASTER)) {
                throw new AccessDeniedException("Apenas o Mestre da campanha pode rolar por um NPC nela");
            }
            return new Access(sheet, npcCampaignId, true, actor);
        }
        CampaignMember owner = sheet.getCampaignMember();
        UUID campaignId = owner.getCampaign().getId();
        CampaignMember requester = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        boolean master = requester.getRole() == Role.MASTER;
        if (!master && !owner.getUser().getId().equals(requesterId)) {
            throw new AccessDeniedException("Você só pode rolar dados na sua própria ficha");
        }
        return new Access(sheet, campaignId, master, requester.getUser());
    }

    private static RollChannel channelOf(RollChannel requested, boolean master) {
        RollChannel c = requested == null ? RollChannel.PUBLIC : requested;
        if (c == RollChannel.GM_PRIVATE && !master) {
            throw new AccessDeniedException("Apenas o Mestre pode rolar no canal privado");
        }
        return c;
    }

    /**
     * Canal CUSTOM: exige campanha, canal da campanha (404) e requester membro dele ou Mestre (403).
     * Devolve o id do canal (null para os demais canais).
     */
    private UUID customChannelOf(RollChannel c, UUID customChannelId, UUID campaignId, boolean master, UUID requesterId) {
        if (c != RollChannel.CUSTOM) return null;
        if (campaignId == null || customChannelId == null) {
            throw new InvalidRollException("Informe a campanha e o canal personalizado");
        }
        var ch = customChannels.findById(customChannelId).filter(x -> campaignId.equals(x.getCampaignId()))
                .orElseThrow(() -> new RollNotFoundException("Canal não encontrado"));
        if (!master && (ch.getMemberIds() == null || !ch.getMemberIds().contains(requesterId))) {
            throw new AccessDeniedException("Você não participa deste canal");
        }
        return customChannelId;
    }

    /** Rolagem livre por expressão. */
    @Transactional
    public RollMessage roll(UUID sheetId, RollRequest req, UUID requesterId) {
        Access a = access(sheetId, requesterId, req.campaignId());
        RollChannel channel = channelOf(req.channel(), a.master());

        UUID customId = customChannelOf(channel, req.customChannelId(), a.campaignId(), a.master(), requesterId);
        Roll roll = execute(sheetId, a.campaignId(), req.expression(), req.purpose(), requesterId, channel, customId);
        ActorRole role = a.master() ? ActorRole.MASTER : ActorRole.PLAYER;
        activityLog.record(a.sheet(), requesterId, role, ActivityCategory.ROLL, "ROLL", null, null,
                req.expression() + " = " + roll.getTotal(), roll.getId(), req.purpose());
        return finish(a, roll, channel);
    }

    /** Rolagem de perfil (técnica, ataque básico ou teste de atributo), rápida ou detalhada. */
    @Transactional
    public RollMessage testRoll(UUID sheetId, RollTestRequest req, UUID requesterId) {
        Access a = access(sheetId, requesterId, req.campaignId());
        RollChannel channel = channelOf(req.channel(), a.master());
        UUID customId = customChannelOf(channel, req.customChannelId(), a.campaignId(), a.master(), requesterId);
        boolean detailed = req.mode() == RollMode.DETAILED;
        List<RollOverride> overrides = req.overrides() == null ? List.of() : req.overrides();
        if (!overrides.isEmpty()) {
            if (!detailed) throw new InvalidRollException("Edição de valores só no modo detalhado");
            if (req.editReason() == null || req.editReason().isBlank()) {
                throw new InvalidRollException("Informe o motivo da edição dos valores");
            }
        }
        if (req.mode() == RollMode.FREE) throw new InvalidRollException("Modo inválido para teste");

        RollProfileService.Calc calc = profiles.forRequest(a.sheet(), req);
        int adv = RollRules.clampAdvantage(req.advantage() + calc.advantage()
                + (detailed && req.targetDowned() ? 1 : 0));
        Roll roll = profileRoll(a.sheet(), a.campaignId(), calc, adv, overrides, req.editReason(), req.mode(),
                req.purpose(), requesterId, channel, customId);
        String purpose = roll.getPurpose();
        int total = roll.getTotal();
        Integer damageTotal = roll.getDamageTotal();

        ActorRole role = a.master() ? ActorRole.MASTER : ActorRole.PLAYER;
        activityLog.record(a.sheet(), requesterId, role, ActivityCategory.ROLL, "ROLL", null, null,
                roll.getSourceName() + " = " + total + (damageTotal == null ? "" : " / dano " + damageTotal),
                roll.getId(), purpose);
        return finish(a, roll, channel);
    }

    /** Calcula (dados, vantagem, críticos, dano) e persiste a rolagem de um perfil; sem log nem publicação. */
    private Roll profileRoll(CharacterSheet sheet, UUID campaignId, RollProfileService.Calc calc, int adv,
                             List<RollOverride> overrides, String editReason, RollMode mode, String reqPurpose,
                             UUID actorId, RollChannel channel, UUID customId) {
        boolean attack = calc.dice() != null;
        List<Integer> d20s = new ArrayList<>();
        for (int i = 0; i < RollRules.d20Count(adv); i++) d20s.add(die(20));
        int natural = RollRules.pickNatural(adv, d20s);
        boolean crit = RollRules.critical(natural, calc.critMargin());
        boolean fumble = RollRules.fumble(natural);

        List<RollPart> parts = new ArrayList<>();
        parts.add(new RollPart("HIT", d20s.size() == 1 ? "d20" : "2d20 (" + (adv > 0 ? "maior" : "menor") + ")",
                natural, d20s, false));
        int bonus = RollRules.advantageBonus(adv);
        if (bonus != 0) parts.add(new RollPart("HIT", "Vantagem líquida " + RollRules.signed(adv), bonus, null, false));
        parts.addAll(calc.hit());

        List<Integer> allDice = new ArrayList<>(d20s);
        int damageCount = 0;
        int damageSides = 0;
        if (attack) {
            damageCount = calc.dice().count() * (crit ? 2 : 1);
            damageSides = calc.dice().sides();
            List<Integer> faces = new ArrayList<>();
            for (int i = 0; i < damageCount; i++) faces.add(die(damageSides));
            allDice.addAll(faces);
            parts.add(new RollPart("DAMAGE", damageCount + "d" + damageSides + " (" + calc.name() + ")",
                    faces.stream().mapToInt(Integer::intValue).sum(), faces, false));
            parts.addAll(calc.damage());
        }
        for (RollOverride ov : overrides) applyOverride(parts, ov);

        int hitFlat = parts.stream().filter(p -> p.dice() == null && "HIT".equals(p.part()))
                .mapToInt(RollPart::value).sum();
        int total = parts.stream().filter(p -> "HIT".equals(p.part())).mapToInt(RollPart::value).sum();
        Integer damageTotal = null;
        String formula = (d20s.size() == 1 ? "1d20" : "2d20") + (hitFlat == 0 ? "" : RollRules.signed(hitFlat));
        if (attack) {
            int dmg = parts.stream().filter(p -> "DAMAGE".equals(p.part())).mapToInt(RollPart::value).sum();
            damageTotal = Math.max(0, dmg);
            int dmgFlat = parts.stream().filter(p -> p.dice() == null && "DAMAGE".equals(p.part()))
                    .mapToInt(RollPart::value).sum();
            formula += " | dano " + damageCount + "d" + damageSides + (dmgFlat == 0 ? "" : RollRules.signed(dmgFlat));
        }
        boolean edited = parts.stream().anyMatch(RollPart::edited);
        String purpose = reqPurpose == null || reqPurpose.isBlank() ? calc.name() : reqPurpose;
        String expression = formula.length() > 200 ? formula.substring(0, 200) : formula;

        Roll roll = rollRepository.save(Roll.builder()
                .campaignId(campaignId).sheetId(sheet.getId()).actorId(actorId)
                .expression(expression).diceResults(allDice.toString().replace(" ", ""))
                .modifier(hitFlat).total(total).naturalD20(natural).purpose(purpose)
                .channel(channel).customChannelId(customId).mode(mode).sourceType(calc.type()).sourceId(calc.sourceId())
                .sourceName(calc.variation() == null ? calc.name() : calc.name() + " (" + calc.variation() + ")")
                .advantage(adv).critical(crit).fumble(fumble).breakdown(parts).damageTotal(damageTotal)
                .editReason(edited ? editReason : null).revealed(false).build());

        return roll;
    }

    /**
     * Rola um perfil internamente (combate F6b): persiste, registra no log da ficha e publica a mensagem
     * (canal PUBLIC) após o commit. Sem checagem de acesso (o chamador valida).
     */
    @Transactional
    public RollMessage rollCalc(CharacterSheet sheet, UUID campaignId, RollProfileService.Calc calc, int extraAdvantage,
                                String purpose, UUID actorId, ActorRole role) {
        int adv = RollRules.clampAdvantage(calc.advantage() + extraAdvantage);
        Roll roll = profileRoll(sheet, campaignId, calc, adv, List.of(), null, RollMode.QUICK, purpose, actorId,
                RollChannel.PUBLIC, null);
        activityLog.record(sheet, actorId, role, ActivityCategory.ROLL, "ROLL", null, null,
                roll.getSourceName() + " = " + roll.getTotal()
                        + (roll.getDamageTotal() == null ? "" : " / dano " + roll.getDamageTotal()),
                roll.getId(), roll.getPurpose());
        String actor = userRepository.findById(actorId).map(User::getName).orElse(null);
        RollMessage msg = toMessage(roll, actor, sheet.getName());
        if (campaignId != null) events.publishEvent(msg);
        return msg;
    }

    /** Mensagem de uma rolagem já gravada (para respostas de combate). */
    @Transactional(readOnly = true)
    public RollMessage message(UUID rollId) {
        return toMessage(rollRepository.findById(rollId).orElseThrow(RollNotFoundException::new));
    }

    /** Substitui o valor de uma parcela não-dado com o mesmo `source` ou adiciona uma parcela HIT nova. */
    private static void applyOverride(List<RollPart> parts, RollOverride ov) {
        for (int i = 0; i < parts.size(); i++) {
            RollPart p = parts.get(i);
            if (p.source().equals(ov.source())) {
                if (p.dice() != null) throw new InvalidRollException("Não é possível editar o resultado de um dado");
                parts.set(i, p.withValue(ov.value()));
                return;
            }
        }
        int at = parts.size();
        for (int i = 0; i < parts.size(); i++) {
            if ("DAMAGE".equals(parts.get(i).part())) { at = i; break; }
        }
        parts.add(at, new RollPart("HIT", ov.source(), ov.value(), null, true));
    }

    /** Perfis de rolagem da ficha (técnicas ACTIVE por variação + ataques básicos). */
    @Transactional(readOnly = true)
    public List<RollProfileResponse> profiles(UUID sheetId, UUID requesterId) {
        Access a = access(sheetId, requesterId, null);
        return profiles.list(a.sheet()).stream().map(RollProfileService::toResponse).toList();
    }

    /** Rolagem livre do Mestre na campanha, sem ficha. */
    @Transactional
    public RollMessage campaignRoll(UUID campaignId, CampaignRollRequest req, UUID requesterId) {
        User actor = requireMaster(campaignId, requesterId);
        RollChannel channel = req.channel() == null ? RollChannel.PUBLIC : req.channel();
        UUID customId = customChannelOf(channel, req.customChannelId(), campaignId, true, requesterId);
        Roll roll = execute(null, campaignId, req.expression(), req.purpose(), requesterId, channel, customId);
        RollMessage msg = toMessage(roll, actor.getName(), null);
        events.publishEvent(msg);
        return msg;
    }

    /** Últimas rolagens da campanha: PUBLIC para membros; GM_PRIVATE só para o Mestre. */
    @Transactional(readOnly = true)
    public List<RollMessage> history(UUID campaignId, RollChannel channel, UUID customChannelId, UUID requesterId) {
        CampaignMember member = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        RollChannel c = channel == null ? RollChannel.PUBLIC : channel;
        if (c == RollChannel.GM_PRIVATE && member.getRole() != Role.MASTER) {
            throw new AccessDeniedException("Apenas o Mestre vê as rolagens privadas");
        }
        UUID customId = customChannelOf(c, customChannelId, campaignId, member.getRole() == Role.MASTER, requesterId);
        var page = PageRequest.of(0, HISTORY_SIZE);
        List<Roll> rolls = c == RollChannel.PUBLIC ? rollRepository.findPublic(campaignId, page)
                : c == RollChannel.CUSTOM ? rollRepository.findCustom(customId, page)
                : rollRepository.findPrivate(campaignId, page);
        Map<UUID, String> users = userRepository.findAllById(
                rolls.stream().map(Roll::getActorId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
        Map<UUID, String> sheets = sheetRepository.findAllById(
                rolls.stream().map(Roll::getSheetId).filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .filter(s -> s.getName() != null)
                .collect(Collectors.toMap(CharacterSheet::getId, CharacterSheet::getName));
        return rolls.stream().map(r -> toMessage(r, users.get(r.getActorId()), sheets.get(r.getSheetId()))).toList();
    }

    /** Torna pública uma rolagem privada do Mestre. */
    @Transactional
    public RollMessage reveal(UUID campaignId, UUID rollId, UUID requesterId) {
        requireMaster(campaignId, requesterId);
        Roll roll = rollRepository.findById(rollId)
                .filter(r -> campaignId.equals(r.getCampaignId())).orElseThrow(RollNotFoundException::new);
        if (roll.getChannel() != RollChannel.GM_PRIVATE && roll.getChannel() != RollChannel.CUSTOM) throw new InvalidRollException("A rolagem já é pública");
        RollMessage msg;
        if (Boolean.TRUE.equals(roll.getRevealed())) {
            msg = toMessage(roll);
        } else {
            roll.setRevealed(true);
            msg = toMessage(rollRepository.save(roll));
            events.publishEvent(msg);
        }
        return msg;
    }

    private User requireMaster(UUID campaignId, UUID requesterId) {
        CampaignMember m = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
        if (m.getRole() != Role.MASTER) throw new AccessDeniedException("Apenas o Mestre pode fazer isto");
        return m.getUser();
    }

    /** Publica (se houver campanha) e devolve a mensagem. */
    private RollMessage finish(Access a, Roll roll, RollChannel channel) {
        RollMessage msg = toMessage(roll, a.actor().getName(), a.sheet().getName());
        if (a.campaignId() != null) {
            if (!a.sheet().isNpcSheet() && channel == RollChannel.PUBLIC) { // rolagem de NPC não vaza
                events.publishEvent(new SheetUpdateMessage(SheetUpdateMessage.SheetEvent.ROLL, a.campaignId(),
                        a.sheet().getId(), ActivityCategory.ROLL, "ROLL", a.actor().getName()));
            }
            events.publishEvent(msg);
        }
        return msg;
    }

    private RollMessage toMessage(Roll r) {
        String actor = userRepository.findById(r.getActorId()).map(User::getName).orElse(null);
        String character = r.getSheetId() == null ? null
                : sheetRepository.findById(r.getSheetId()).map(CharacterSheet::getName).orElse(null);
        return toMessage(r, actor, character);
    }

    static RollMessage toMessage(Roll r, String actorName, String characterName) {
        List<Integer> dice = new ArrayList<>();
        String raw = r.getDiceResults() == null ? "" : r.getDiceResults().replaceAll("[\\[\\]\\s]", "");
        if (!raw.isEmpty()) for (String d : raw.split(",")) dice.add(Integer.parseInt(d));
        List<RollPart> parts = r.getBreakdown() == null ? List.of() : r.getBreakdown();
        return new RollMessage(r.getId(), r.getCampaignId(), r.getSheetId(), r.getActorId(), actorName,
                characterName, r.getChannel() == null ? RollChannel.PUBLIC : r.getChannel(),
                r.getMode() == null ? RollMode.FREE : r.getMode(), r.getPurpose(),
                r.getSourceType() == null ? RollSourceType.CUSTOM : r.getSourceType(), r.getSourceId(),
                r.getSourceName(), r.getExpression(), r.getExpression(), r.getModifier(), parts, dice,
                r.getAdvantage() == null ? 0 : r.getAdvantage(), r.getTotal(), r.getNaturalD20(),
                Boolean.TRUE.equals(r.getCritical()) || (r.getCritical() == null && Integer.valueOf(20).equals(r.getNaturalD20())),
                Boolean.TRUE.equals(r.getFumble()) || (r.getFumble() == null && Integer.valueOf(1).equals(r.getNaturalD20())),
                r.getDamageTotal(), parts.stream().anyMatch(RollPart::edited), r.getEditReason(),
                Boolean.TRUE.equals(r.getRevealed()), r.getCreatedAt(), r.getCustomChannelId());
    }

    /**
     * Rolagem feita pelo servidor em nome de um fluxo interno (ex.: Construção Inicial): grava a
     * {@link Roll} e o log ROLL, mas NÃO publica evento. Sem checagem de acesso (o chamador valida).
     */
    @Transactional
    public Roll rollInternal(CharacterSheet sheet, String expression, String purpose, UUID actorId, ActorRole role) {
        UUID campaignId = sheet.isNpcSheet() ? null : sheet.getCampaignMember().getCampaign().getId();
        Roll roll = execute(sheet.getId(), campaignId, expression, purpose, actorId, RollChannel.PUBLIC, null);
        activityLog.record(sheet, actorId, role, ActivityCategory.ROLL, "ROLL", null, null,
                expression + " = " + roll.getTotal(), roll.getId(), purpose);
        return roll;
    }

    private static int die(int sides) {
        return RANDOM.nextInt(sides) + 1;
    }

    private Roll execute(UUID sheetId, UUID campaignId, String expression, String purpose, UUID actorId,
                         RollChannel channel, UUID customChannelId) {
        DiceExpression expr = parse(expression);
        List<Integer> dice = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i < expr.count(); i++) {
            int d = die(expr.sides());
            dice.add(d);
            sum += d;
        }
        int total = sum + expr.modifier();
        Integer natural = expr.count() == 1 && expr.sides() == 20 ? dice.get(0) : null;

        return rollRepository.save(Roll.builder()
                .campaignId(campaignId)
                .sheetId(sheetId)
                .actorId(actorId)
                .expression(expression)
                .diceResults(dice.toString().replace(" ", ""))
                .modifier(expr.modifier())
                .total(total)
                .naturalD20(natural)
                .purpose(purpose)
                .channel(channel).customChannelId(customChannelId).mode(RollMode.FREE).sourceType(RollSourceType.CUSTOM)
                .advantage(0)
                .critical(natural != null && natural == 20).fumble(natural != null && natural == 1)
                .revealed(false)
                .build());
    }

    /** Interpreta "NdM+K"; N entre 1 e 20, M >= 2. */
    public static DiceExpression parse(String expression) {
        Matcher m = expression == null ? null : EXPRESSION.matcher(expression.trim());
        if (m == null || !m.matches()) {
            throw new InvalidRollException("Expressão de dados inválida");
        }
        int count = Integer.parseInt(m.group(1));
        int sides = Integer.parseInt(m.group(2));
        int modifier = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        if (count < 1 || count > 20 || sides < 2) {
            throw new InvalidRollException("Expressão de dados inválida: use de 1 a 20 dados de 2+ lados");
        }
        return new DiceExpression(count, sides, modifier);
    }
}
