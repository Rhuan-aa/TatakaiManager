package com.tatakai.manager.service;

import com.tatakai.manager.dto.request.UpdateCampaignRuleRequest;
import com.tatakai.manager.dto.response.CampaignRuleResponse;
import com.tatakai.manager.entity.BuildSettings;
import com.tatakai.manager.entity.CampaignRuleSetting;
import com.tatakai.manager.entity.OptionalRule;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignRepository;
import com.tatakai.manager.repository.CampaignRuleSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class CampaignRuleService {

    private static final Set<String> TRAIT_METHODS = Set.of("CLASSIC", "BALANCED_CHOICE", "PRODIGY");
    private static final Set<String> DIVINE_MODES = Set.of("ROLL_100_GRANTS_DIVINE", "FREE_DIVINE_TRAIT");

    private final CampaignRuleSettingRepository ruleRepository;
    private final CampaignMemberRepository memberRepository;
    private final CampaignRepository campaignRepository;

    public CampaignRuleService(CampaignRuleSettingRepository ruleRepository,
                               CampaignMemberRepository memberRepository,
                               CampaignRepository campaignRepository) {
        this.ruleRepository = ruleRepository;
        this.memberRepository = memberRepository;
        this.campaignRepository = campaignRepository;
    }

    @Transactional(readOnly = true)
    public List<CampaignRuleResponse> list(UUID campaignId, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserId(campaignId, requesterId)) {
            throw new AccessDeniedException("Você não é membro desta campanha");
        }
        Map<OptionalRule, CampaignRuleSetting> saved = new HashMap<>();
        for (CampaignRuleSetting s : ruleRepository.findByCampaignId(campaignId)) {
            saved.put(s.getRuleKey(), s);
        }
        return Arrays.stream(OptionalRule.values()).map(r -> toResponse(r, saved.get(r))).toList();
    }

    public CampaignRuleResponse update(UUID campaignId, OptionalRule rule,
                                       UpdateCampaignRuleRequest req, UUID requesterId) {
        if (!memberRepository.existsByCampaignIdAndUserIdAndRole(campaignId, requesterId, Role.MASTER)) {
            throw new AccessDeniedException("Apenas o Mestre da campanha pode realizar esta ação");
        }
        Map<String, String> params = req.params() == null ? new HashMap<>() : new HashMap<>(req.params());
        if (rule == OptionalRule.TRAIT_METHOD && req.enabled()) {
            requireParam(params, "method", TRAIT_METHODS, "Método de traços inválido");
        }
        if (rule == OptionalRule.DIVINE_INTERVENTION && req.enabled()) {
            requireParam(params, "mode", DIVINE_MODES, "Modo de intervenção divina inválido");
        }
        CampaignRuleSetting s = ruleRepository.findByCampaignIdAndRuleKey(campaignId, rule)
                .orElseGet(() -> {
                    CampaignRuleSetting n = new CampaignRuleSetting();
                    n.setCampaign(campaignRepository.getReferenceById(campaignId));
                    n.setRuleKey(rule);
                    return n;
                });
        s.setEnabled(req.enabled());
        s.setParams(params);
        return toResponse(rule, ruleRepository.save(s));
    }

    @Transactional(readOnly = true)
    public BuildSettings buildSettings(UUID campaignId) {
        CampaignRuleSetting method = ruleRepository.findByCampaignIdAndRuleKey(campaignId, OptionalRule.TRAIT_METHOD).orElse(null);
        CampaignRuleSetting divine = ruleRepository.findByCampaignIdAndRuleKey(campaignId, OptionalRule.DIVINE_INTERVENTION).orElse(null);
        CampaignRuleResponse tm = toResponse(OptionalRule.TRAIT_METHOD, method);
        String traitMethod = tm.enabled() ? tm.params().getOrDefault("method", "CLASSIC") : "CLASSIC";
        String divineMode = divine != null && divine.isEnabled()
                ? (divine.getParams() == null ? null : divine.getParams().get("mode")) : null;
        return new BuildSettings(traitMethod, divineMode,
                isEnabled(campaignId, OptionalRule.MAX_RANDOM_CLAN),
                isEnabled(campaignId, OptionalRule.MAX_RANDOM_ATTRIBUTES),
                isEnabled(campaignId, OptionalRule.GM_REROLLS));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID campaignId, OptionalRule rule) {
        return toResponse(rule, ruleRepository.findByCampaignIdAndRuleKey(campaignId, rule).orElse(null)).enabled();
    }

    private static void requireParam(Map<String, String> params, String key, Set<String> allowed, String msg) {
        String value = params == null ? null : params.get(key);
        if (value == null || !allowed.contains(value)) {
            throw new InvalidCharacterSheetException(msg);
        }
    }

    private static CampaignRuleResponse toResponse(OptionalRule rule, CampaignRuleSetting s) {
        if (s != null) {
            return new CampaignRuleResponse(rule, s.isEnabled(), s.getParams() == null ? Map.of() : s.getParams());
        }
        return switch (rule) {
            case GM_REROLLS -> new CampaignRuleResponse(rule, true, Map.of());
            case TRAIT_METHOD -> new CampaignRuleResponse(rule, true, Map.of("method", "CLASSIC"));
            default -> new CampaignRuleResponse(rule, false, Map.of());
        };
    }
}
