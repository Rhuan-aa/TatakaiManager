package com.tatakai.manager.roll;

import com.tatakai.manager.catalog.repository.MartialArtRepository;
import com.tatakai.manager.combat.entity.BasicAttack;
import com.tatakai.manager.martial.repository.CharacterTechniqueRepository;
import com.tatakai.manager.roll.service.RollProfileService;
import com.tatakai.manager.roll.service.RollProfileService.Calc;
import com.tatakai.manager.roll.service.RollProfileService.Opts;
import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.InventoryItem;
import com.tatakai.manager.sheet.entity.ItemLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RollProfileServiceTest {

    @Mock CharacterTechniqueRepository techniqueRepository;
    @Mock MartialArtRepository martialArtRepository;

    private CharacterSheet sheet() {
        return CharacterSheet.builder().id(UUID.randomUUID()).str(30).dex(30).inventory(new ArrayList<>()).build();
    }

    @Test
    @DisplayName("Ataques básicos desarmados (inclusive Empurrão/Agarrão) custam 1 SP no perfil")
    void basicosDesarmadosCustamUmSp() {
        var service = new RollProfileService(techniqueRepository, martialArtRepository);
        CharacterSheet sheet = sheet();
        for (BasicAttack b : BasicAttack.values()) {
            if (!b.unarmed()) continue;
            Calc c = service.basic(sheet, b, Opts.NONE);
            assertThat(c.spCost()).as(b.name()).isEqualTo(1);
            assertThat(RollProfileService.toResponse(c).spCost()).as(b.name()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Ataque com arma custa 1 SP")
    void armaCustaUmSp() {
        var service = new RollProfileService(techniqueRepository, martialArtRepository);
        CharacterSheet sheet = sheet();
        InventoryItem garrafa = InventoryItem.builder().id(UUID.randomUUID()).sheet(sheet).name("Garrafa")
                .quantity(1).weaponKey("GARRAFA").location(ItemLocation.HAND).build();
        sheet.setInventory(List.of(garrafa));
        assertThat(service.weaponOf(sheet, garrafa.getId(), null, Opts.NONE).spCost()).isEqualTo(1);
    }
}
