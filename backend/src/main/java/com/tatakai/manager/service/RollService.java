package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.RollRequest;
import com.tatakai.manager.dto.response.RollResponse;
import com.tatakai.manager.dto.response.SheetUpdateMessage;
import com.tatakai.manager.entity.ActivityCategory;
import com.tatakai.manager.entity.ActorRole;
import com.tatakai.manager.entity.CampaignMember;
import com.tatakai.manager.entity.CharacterSheet;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.entity.Roll;
import com.tatakai.manager.entity.User;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.CharacterSheetNotFoundException;
import com.tatakai.manager.exception.InvalidRollException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CharacterSheetRepository;
import com.tatakai.manager.repository.RollRepository;
import com.tatakai.manager.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RollService {

    private static final Pattern EXPRESSION = Pattern.compile("^(\\d{1,2})d(\\d{1,3})([+-]\\d{1,3})?$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record DiceExpression(int count, int sides, int modifier) {}

    private final RollRepository rollRepository;
    private final CharacterSheetRepository sheetRepository;
    private final CampaignMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final SheetActivityLogService activityLog;
    private final ApplicationEventPublisher events;

    public RollService(RollRepository rollRepository,
                       CharacterSheetRepository sheetRepository,
                       CampaignMemberRepository memberRepository,
                       UserRepository userRepository,
                       SheetActivityLogService activityLog,
                       ApplicationEventPublisher events) {
        this.rollRepository = rollRepository;
        this.sheetRepository = sheetRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.activityLog = activityLog;
        this.events = events;
    }

    @Transactional
    public RollResponse roll(UUID sheetId, RollRequest req, UUID requesterId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId)
                .orElseThrow(CharacterSheetNotFoundException::new);
        boolean npcSheet = sheet.isNpcSheet();
        UUID campaignId = null;
        boolean isMaster;
        User actor;
        if (npcSheet) {
            if (!requesterId.equals(sheet.getNpcOwnerId())) throw new CharacterSheetNotFoundException();
            isMaster = true;
            actor = userRepository.findById(requesterId).orElseThrow(CharacterSheetNotFoundException::new);
        } else {
            CampaignMember owner = sheet.getCampaignMember();
            campaignId = owner.getCampaign().getId();
            CampaignMember requester = memberRepository.findByCampaignIdAndUserId(campaignId, requesterId)
                    .orElseThrow(() -> new AccessDeniedException("Você não pertence a esta campanha"));
            isMaster = requester.getRole() == Role.MASTER;
            if (!isMaster && !owner.getUser().getId().equals(requesterId)) {
                throw new AccessDeniedException("Você só pode rolar dados na sua própria ficha");
            }
            actor = requester.getUser();
        }

        Rolled rolled = execute(sheet, campaignId, req.expression(), req.purpose(), requesterId);
        Roll roll = rolled.roll();
        List<Integer> dice = rolled.dice();
        DiceExpression expr = rolled.expr();
        int total = roll.getTotal();
        Integer natural = roll.getNaturalD20();

        ActorRole role = isMaster ? ActorRole.MASTER : ActorRole.PLAYER;
        activityLog.record(sheet, requesterId, role, ActivityCategory.ROLL, "ROLL", null, null,
                req.expression() + " = " + total, roll.getId(), req.purpose());

        if (!npcSheet) { // rolagem de NPC não vaza para os jogadores
            events.publishEvent(new SheetUpdateMessage(SheetUpdateMessage.SheetEvent.ROLL, campaignId,
                    sheetId, ActivityCategory.ROLL, "ROLL", actor.getName()));
        }

        return new RollResponse(roll.getId(), sheetId, requesterId, actor.getName(), req.expression(),
                dice, expr.modifier(), total, natural,
                natural != null && natural == 20, natural != null && natural == 1,
                req.purpose(), roll.getCreatedAt());
    }

    /**
     * Rolagem feita pelo servidor em nome de um fluxo interno (ex.: Construção Inicial): grava a
     * {@link Roll} e o log ROLL, mas NÃO publica evento. Sem checagem de acesso (o chamador valida).
     */
    @Transactional
    public Roll rollInternal(CharacterSheet sheet, String expression, String purpose, UUID actorId, ActorRole role) {
        UUID campaignId = sheet.isNpcSheet() ? null : sheet.getCampaignMember().getCampaign().getId();
        Roll roll = execute(sheet, campaignId, expression, purpose, actorId).roll();
        activityLog.record(sheet, actorId, role, ActivityCategory.ROLL, "ROLL", null, null,
                expression + " = " + roll.getTotal(), roll.getId(), purpose);
        return roll;
    }

    private record Rolled(Roll roll, List<Integer> dice, DiceExpression expr) {}

    private Rolled execute(CharacterSheet sheet, UUID campaignId, String expression, String purpose, UUID actorId) {
        DiceExpression expr = parse(expression);
        List<Integer> dice = new ArrayList<>();
        int sum = 0;
        for (int i = 0; i < expr.count(); i++) {
            int d = RANDOM.nextInt(expr.sides()) + 1;
            dice.add(d);
            sum += d;
        }
        int total = sum + expr.modifier();
        Integer natural = expr.count() == 1 && expr.sides() == 20 ? dice.get(0) : null;

        Roll roll = rollRepository.save(Roll.builder()
                .campaignId(campaignId)
                .sheetId(sheet.getId())
                .actorId(actorId)
                .expression(expression)
                .diceResults(dice.toString().replace(" ", ""))
                .modifier(expr.modifier())
                .total(total)
                .naturalD20(natural)
                .purpose(purpose)
                .build());
        return new Rolled(roll, dice, expr);
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
