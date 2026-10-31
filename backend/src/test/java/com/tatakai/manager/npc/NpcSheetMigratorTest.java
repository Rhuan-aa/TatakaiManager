package com.tatakai.manager.npc;

import com.tatakai.manager.npc.entity.CampaignNpc;
import com.tatakai.manager.npc.repository.CampaignNpcRepository;
import com.tatakai.manager.catalog.entity.Graduation;
import com.tatakai.manager.catalog.repository.MartialArtRepository;
import com.tatakai.manager.martial.repository.CharacterArtRepository;
import com.tatakai.manager.npc.entity.Npc;
import com.tatakai.manager.npc.entity.NpcAttributes;
import com.tatakai.manager.npc.entity.NpcDetail;
import com.tatakai.manager.npc.repository.NpcRepository;
import com.tatakai.manager.npc.service.NpcSheetMigrator;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.CharacterTrait;
import com.tatakai.manager.sheet.entity.NpcSheetVisibility;
import com.tatakai.manager.sheet.repository.CharacterSheetRepository;
import com.tatakai.manager.sheet.repository.NpcSheetVisibilityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Migração de NPC para a ficha única (§51.8)")
class NpcSheetMigratorTest {

    private final CharacterSheetRepository sheets = Mockito.mock(CharacterSheetRepository.class);
    private final CharacterArtRepository arts = Mockito.mock(CharacterArtRepository.class);
    private final CampaignNpcRepository campaignNpcs = Mockito.mock(CampaignNpcRepository.class);
    private final NpcSheetVisibilityRepository visibility = Mockito.mock(NpcSheetVisibilityRepository.class);
    private final NpcSheetMigrator migrator = new NpcSheetMigrator(Mockito.mock(NpcRepository.class), sheets, arts,
            Mockito.mock(MartialArtRepository.class), campaignNpcs, visibility, null);

    private Npc legacyNpc() {
        NpcAttributes attrs = new NpcAttributes();
        attrs.setForca((short) 14);
        attrs.setTalento((short) 9);
        NpcDetail trait = new NpcDetail();
        trait.setName("Cicatriz");
        trait.setDescription("de guerra");
        return Npc.builder().id(UUID.randomUUID()).ownerId(UUID.randomUUID()).name("Vael").attributes(attrs)
                .traits(new ArrayList<>(List.of(trait))).martialArtId(UUID.randomUUID())
                .martialGraduation(Graduation.INTERMEDIATE).build();
    }

    @Test
    @DisplayName("sem ficha: cria leve com atributos pré-preenchidos, traço em texto, arte e visibilidade dos traços")
    void createsLightSheet() {
        Npc npc = legacyNpc();
        CampaignNpc cn = CampaignNpc.builder().id(UUID.randomUUID()).npcId(npc.getId()).visible(true).build();
        when(sheets.findByNpcId(npc.getId())).thenReturn(Optional.empty());
        when(sheets.save(any(CharacterSheet.class))).thenAnswer(i -> {
            CharacterSheet s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(campaignNpcs.findByNpcId(npc.getId())).thenReturn(List.of(cn));
        when(visibility.findByCampaignNpcIdAndSection(any(), any())).thenReturn(Optional.empty());

        migrator.migrate(npc);

        var saved = org.mockito.ArgumentCaptor.forClass(CharacterSheet.class);
        verify(sheets).save(saved.capture());
        CharacterSheet s = saved.getValue();
        assertThat(s.isCombatReady()).isFalse();
        assertThat(s.getStr()).isEqualTo(14);
        assertThat(s.getTal()).isEqualTo(9);
        assertThat(s.getTraits()).extracting(CharacterTrait::getName).containsExactly("Cicatriz");
        verify(arts).save(any());
        verify(visibility).save(any(NpcSheetVisibility.class));
    }

    @Test
    @DisplayName("idempotente: ficha completa mantém atributos e traço de mesmo nome não duplica")
    void idempotentAndKeepsFullSheet() {
        Npc npc = legacyNpc();
        CharacterSheet full = CharacterSheet.builder().id(UUID.randomUUID()).npcId(npc.getId()).str(30).build();
        full.getTraits().add(CharacterTrait.builder().sheet(full).name("Cicatriz").build());
        when(sheets.findByNpcId(npc.getId())).thenReturn(Optional.of(full));
        when(sheets.save(any(CharacterSheet.class))).thenAnswer(i -> i.getArgument(0));
        when(arts.findBySheetIdOrderByCreatedAt(full.getId())).thenReturn(List.of(
                com.tatakai.manager.martial.entity.CharacterArt.builder().artId(npc.getMartialArtId()).build()));

        migrator.migrate(npc);

        assertThat(full.getStr()).isEqualTo(30);
        assertThat(full.getTraits()).hasSize(1);
        verify(arts, never()).save(any());
        verify(visibility, never()).save(any());
        assertThat(NpcSheetMigrator.clearLegacy(npc)).isTrue();
        assertThat(npc.getAttributes()).isNull();
        assertThat(NpcSheetMigrator.clearLegacy(npc)).isFalse();
    }
}
