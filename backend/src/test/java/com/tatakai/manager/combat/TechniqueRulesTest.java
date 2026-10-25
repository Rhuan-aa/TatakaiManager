package com.tatakai.manager.combat;

import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.AssemblyItemKind;
import com.tatakai.manager.entity.ReactionType;
import com.tatakai.manager.service.rules.TechniqueRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TechniqueRules — técnicas de Reação e Preparação (§38)")
class TechniqueRulesTest {

    private static AssemblyItem it(String module, String label, AssemblyItemKind kind) {
        AssemblyItem i = new AssemblyItem();
        i.setModule(module);
        i.setLabel(label);
        i.setKind(kind);
        i.setQty(1);
        return i;
    }

    @Test
    @DisplayName("Aú (Capoeira): Esquiva +2, Reposicionar, Abertura")
    void au() {
        TechniqueRules.Reaction r = TechniqueRules.reaction(List.of(
                it("Reforço", "Reforço +2 em Esquiva", AssemblyItemKind.MODULE),
                it("Reposicionar", "Reposicionar", AssemblyItemKind.MODULE),
                it("Abertura", "Abertura", AssemblyItemKind.MODULE)));
        assertThat(r.type()).isEqualTo(ReactionType.DODGE);
        assertThat(r.bonus()).isEqualTo(2);
        assertThat(r.opening()).isTrue();
        assertThat(r.punitive()).isFalse();
    }

    @Test
    @DisplayName("Gedan Barai: Defender +2, Bloqueio Punitivo, gatilho; Arae Makgi sem Reforço vira Defender")
    void defendTechniques() {
        TechniqueRules.Reaction g = TechniqueRules.reaction(List.of(
                it("Reforço", "Reforço +2 em Defender", AssemblyItemKind.MODULE),
                it("Bloqueio Punitivo", "Bloqueio Punitivo", AssemblyItemKind.MODULE),
                it("Gatilho", "Gatilho: ataque baixo", AssemblyItemKind.LIMITATION)));
        assertThat(g.type()).isEqualTo(ReactionType.DEFEND);
        assertThat(g.punitive()).isTrue();
        assertThat(g.triggers()).containsExactly("Gatilho: ataque baixo");
        TechniqueRules.Reaction a = TechniqueRules.reaction(List.of(
                it("Redução Escalonada", "Redução Escalonada", AssemblyItemKind.MODULE)));
        assertThat(a.type()).isEqualTo(ReactionType.DEFEND);
        assertThat(a.escalated()).isTrue();
        assertThat(TechniqueRules.stepUp(25)).isEqualTo(50);
        assertThat(TechniqueRules.stepUp(75)).isEqualTo(100);
        assertThat(TechniqueRules.stepUp(100)).isEqualTo(100);
    }

    @Test
    @DisplayName("Dempsey Roll (Preparação): Esquiva +4 e Carga +6")
    void dempsey() {
        TechniqueRules.Preparation p = TechniqueRules.preparation(List.of(
                it("Reforço", "Reforço +4 em Esquiva", AssemblyItemKind.MODULE),
                it("Carga", "Carga +6", AssemblyItemKind.MODULE)));
        assertThat(p.reinforce()).containsEntry(ReactionType.DODGE, 4);
        assertThat(p.charge()).isEqualTo(6);
        assertThat(p.reading()).isFalse();
    }
}
