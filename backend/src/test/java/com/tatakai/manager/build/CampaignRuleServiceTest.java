package com.tatakai.manager.build;

import com.tatakai.manager.dto.request.UpdateCampaignRuleRequest;
import com.tatakai.manager.dto.response.CampaignRuleResponse;
import com.tatakai.manager.entity.BuildSettings;
import com.tatakai.manager.entity.Campaign;
import com.tatakai.manager.entity.CampaignRuleSetting;
import com.tatakai.manager.entity.OptionalRule;
import com.tatakai.manager.entity.Role;
import com.tatakai.manager.exception.AccessDeniedException;
import com.tatakai.manager.exception.InvalidCharacterSheetException;
import com.tatakai.manager.repository.CampaignMemberRepository;
import com.tatakai.manager.repository.CampaignRepository;
import com.tatakai.manager.repository.CampaignRuleSettingRepository;
import com.tatakai.manager.service.CampaignRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignRuleService — Sprint 10 — F2 Construção Inicial")
class CampaignRuleServiceTest {

    @Mock private CampaignRuleSettingRepository ruleRepository;
    @Mock private CampaignMemberRepository memberRepository;
    @Mock private CampaignRepository campaignRepository;

    private CampaignRuleService service;
    private final UUID cid = UUID.randomUUID();
    private final UUID master = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CampaignRuleService(ruleRepository, memberRepository, campaignRepository);
        lenient().when(memberRepository.existsByCampaignIdAndUserIdAndRole(cid, master, Role.MASTER)).thenReturn(true);
        lenient().when(memberRepository.existsByCampaignIdAndUserId(cid, master)).thenReturn(true);
        lenient().when(memberRepository.existsByCampaignIdAndUserId(cid, player)).thenReturn(true);
        lenient().when(ruleRepository.save(any(CampaignRuleSetting.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CampaignRuleSetting saved(OptionalRule r, boolean enabled, Map<String, String> params) {
        CampaignRuleSetting s = CampaignRuleSetting.builder().ruleKey(r).enabled(enabled).params(params).build();
        lenient().when(ruleRepository.findByCampaignIdAndRuleKey(cid, r)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    @DisplayName("padrões: GM_REROLLS ligado, TRAIT_METHOD CLASSIC, demais desligados")
    void defaults() {
        when(ruleRepository.findByCampaignId(cid)).thenReturn(List.of());

        List<CampaignRuleResponse> list = service.list(cid, player);

        assertThat(list).hasSize(OptionalRule.values().length);
        CampaignRuleResponse gm = list.stream().filter(r -> r.ruleKey() == OptionalRule.GM_REROLLS).findFirst().get();
        assertThat(gm.enabled()).isTrue();
        CampaignRuleResponse tm = list.stream().filter(r -> r.ruleKey() == OptionalRule.TRAIT_METHOD).findFirst().get();
        assertThat(tm.enabled()).isTrue();
        assertThat(tm.params()).containsEntry("method", "CLASSIC");
        assertThat(list.stream().filter(r -> r.ruleKey() == OptionalRule.FREE_CREATION).findFirst().get().enabled())
                .isFalse();
        assertThat(service.isEnabled(cid, OptionalRule.GM_REROLLS)).isTrue();
        assertThat(service.isEnabled(cid, OptionalRule.MAX_RANDOM_CLAN)).isFalse();
    }

    @Test
    @DisplayName("listar exige ser membro")
    void list_nonMember() {
        assertThatThrownBy(() -> service.list(cid, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("apenas o Mestre atualiza regras")
    void update_masterOnly() {
        assertThatThrownBy(() -> service.update(cid, OptionalRule.BURNOUT,
                new UpdateCampaignRuleRequest(true, null), player))
                .isInstanceOf(AccessDeniedException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mestre cria a regra quando ainda não existe")
    void update_creates() {
        when(ruleRepository.findByCampaignIdAndRuleKey(cid, OptionalRule.BURNOUT)).thenReturn(Optional.empty());
        when(campaignRepository.getReferenceById(cid)).thenReturn(Campaign.builder().id(cid).build());

        CampaignRuleResponse r = service.update(cid, OptionalRule.BURNOUT,
                new UpdateCampaignRuleRequest(true, null), master);

        assertThat(r.enabled()).isTrue();
        assertThat(r.ruleKey()).isEqualTo(OptionalRule.BURNOUT);
    }

    @Test
    @DisplayName("parâmetros inválidos são rejeitados (método de traços e modo divino)")
    void update_invalidParams() {
        assertThatThrownBy(() -> service.update(cid, OptionalRule.TRAIT_METHOD,
                new UpdateCampaignRuleRequest(true, Map.of("method", "XPTO")), master))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> service.update(cid, OptionalRule.DIVINE_INTERVENTION,
                new UpdateCampaignRuleRequest(true, Map.of("mode", "NOPE")), master))
                .isInstanceOf(InvalidCharacterSheetException.class);
        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("parâmetro ausente com regra ligada é rejeitado (InvalidCharacterSheetException, não NPE)")
    void update_missingParam() {
        assertThatThrownBy(() -> service.update(cid, OptionalRule.TRAIT_METHOD,
                new UpdateCampaignRuleRequest(true, null), master))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> service.update(cid, OptionalRule.DIVINE_INTERVENTION,
                new UpdateCampaignRuleRequest(true, Map.of()), master))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }

    @Test
    @DisplayName("parâmetros válidos são aceitos")
    void update_validParams() {
        when(ruleRepository.findByCampaignIdAndRuleKey(cid, OptionalRule.TRAIT_METHOD)).thenReturn(Optional.empty());
        when(campaignRepository.getReferenceById(cid)).thenReturn(Campaign.builder().id(cid).build());

        CampaignRuleResponse r = service.update(cid, OptionalRule.TRAIT_METHOD,
                new UpdateCampaignRuleRequest(true, Map.of("method", "PRODIGY")), master);

        assertThat(r.params()).containsEntry("method", "PRODIGY");
    }

    @Test
    @DisplayName("buildSettings: padrões")
    void buildSettings_defaults() {
        BuildSettings s = service.buildSettings(cid);
        assertThat(s).isEqualTo(new BuildSettings("CLASSIC", null, false, false, true));
    }

    @Test
    @DisplayName("buildSettings: mapeia regras salvas")
    void buildSettings_mapping() {
        saved(OptionalRule.TRAIT_METHOD, true, Map.of("method", "BALANCED_CHOICE"));
        saved(OptionalRule.DIVINE_INTERVENTION, true, Map.of("mode", "FREE_DIVINE_TRAIT"));
        saved(OptionalRule.MAX_RANDOM_CLAN, true, Map.of());
        saved(OptionalRule.MAX_RANDOM_ATTRIBUTES, true, Map.of());
        saved(OptionalRule.GM_REROLLS, false, Map.of());

        BuildSettings s = service.buildSettings(cid);

        assertThat(s).isEqualTo(new BuildSettings("BALANCED_CHOICE", "FREE_DIVINE_TRAIT", true, true, false));
    }

    @Test
    @DisplayName("buildSettings: TRAIT_METHOD desligado volta a CLASSIC; intervenção divina desligada = null")
    void buildSettings_disabled() {
        saved(OptionalRule.TRAIT_METHOD, false, Map.of("method", "PRODIGY"));
        saved(OptionalRule.DIVINE_INTERVENTION, false, Map.of("mode", "FREE_DIVINE_TRAIT"));

        BuildSettings s = service.buildSettings(cid);

        assertThat(s.traitMethod()).isEqualTo("CLASSIC");
        assertThat(s.divineMode()).isNull();
    }
}
