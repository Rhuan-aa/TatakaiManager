package com.tatakai.manager.catalog;

import com.tatakai.manager.dto.response.AssemblyCheck;
import com.tatakai.manager.entity.ArtBase;
import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.ArtTechnique;
import com.tatakai.manager.entity.AssemblyItem;
import com.tatakai.manager.entity.AssemblyItemKind;
import com.tatakai.manager.entity.AssemblyVariation;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.TechniqueCore;
import com.tatakai.manager.service.rules.AssemblyRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.tatakai.manager.entity.AssemblyItemKind.EFFECT;
import static com.tatakai.manager.entity.AssemblyItemKind.LIMITATION;
import static com.tatakai.manager.entity.AssemblyItemKind.MODULE;
import static com.tatakai.manager.entity.AssemblyItemKind.UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;

class AssemblyRulesTest {

    private static AssemblyItem item(String module, AssemblyItemKind kind, int cost) {
        AssemblyItem i = new AssemblyItem();
        i.setModule(kind == UNIQUE ? null : module);
        i.setLabel(module);
        i.setKind(kind);
        i.setCost(cost);
        return i;
    }

    private static AssemblyVariation variation(TechniqueCore core, AssemblyItem... items) {
        return new AssemblyVariation(null, core, new ArrayList<>(List.of(items)));
    }

    private static ArtTechnique technique(Graduation g, List<AssemblyVariation> vars, AssemblyItem... extras) {
        ArtTechnique t = new ArtTechnique();
        t.setGraduation(g);
        t.setVariations(new ArrayList<>(vars));
        t.setExtras(new ArrayList<>(List.of(extras)));
        return t;
    }

    @Test
    @DisplayName("F3a — RN-TEC-01..04: Mae Geri (Karatê, Iniciante) = 7/7, gasto 11, PT 7, teto clássico 8")
    void maeGeri() {
        AssemblyCheck c = AssemblyRules.checkTechnique(ArtCategory.CLASSIC, technique(Graduation.BEGINNER, List.of(
                variation(TechniqueCore.STRIKE, item("Dado Maior", MODULE, 1), item("Alcance Médio", MODULE, 1),
                        item("Contragolpe", MODULE, 3), item("Chute de Parada", UNIQUE, 2)))));

        assertThat(c.rawComplexity()).isEqualTo(7);
        assertThat(c.finalComplexity()).isEqualTo(7);
        assertThat(c.spCosts()).containsExactly(11);
        assertThat(c.trainingPoints()).isEqualTo(7);
        assertThat(c.ceiling()).isEqualTo(8);
        assertThat(c.issues()).isEmpty();
    }

    @Test
    @DisplayName("F3a — limitação reduz a final mas não o gasto: Liver Shot = 11/10, gasto 15, PT 10")
    void liverShot() {
        AssemblyCheck c = AssemblyRules.checkTechnique(ArtCategory.CLASSIC, technique(Graduation.MASTER, List.of(
                variation(TechniqueCore.STRIKE, item("Dado Maior", MODULE, 2), item("Ponto Vital", MODULE, 2),
                        item("Pressão Fixa", MODULE, 1), item("Derrubar", EFFECT, 2), item("Roubar Tempo", EFFECT, 4),
                        item("Previsível", LIMITATION, -1)))));

        assertThat(c.rawComplexity()).isEqualTo(11);
        assertThat(c.finalComplexity()).isEqualTo(10);
        assertThat(c.spCosts()).containsExactly(15);
        assertThat(c.ceiling()).isEqualTo(16);
    }

    @Test
    @DisplayName("F3a — limitações limitadas a floor(bruta/2)")
    void limitationCap() {
        AssemblyCheck c = AssemblyRules.checkTechnique(ArtCategory.CLASSIC, technique(Graduation.BEGINNER, List.of(
                variation(TechniqueCore.STRIKE, item("Massa", MODULE, 2), item("Investida", MODULE, 2),
                        item("Lenta", LIMITATION, -2), item("Exposta", LIMITATION, -3)))));

        assertThat(c.finalComplexity()).isEqualTo(2);
        assertThat(c.trainingPoints()).isEqualTo(2);
    }

    @Test
    @DisplayName("F3a — duas variações: max(final) + 1, mais extras; gasto por variação (Mawashi Geri = 11)")
    void variationsAndExtras() {
        AssemblyCheck c = AssemblyRules.checkTechnique(ArtCategory.CLASSIC, technique(Graduation.INTERMEDIATE, List.of(
                        variation(TechniqueCore.STRIKE, item("Dado Maior", MODULE, 3), item("Alcance Médio", MODULE, 1),
                                item("Fôlego", MODULE, 2)),
                        variation(TechniqueCore.STRIKE, item("Dado Maior", MODULE, 3), item("Alcance Médio", MODULE, 1),
                                item("Ponto Vital", MODULE, 2), item("Impacto na Cabeça", MODULE, 2))),
                item("Chute Fantasma", UNIQUE, 2)));

        assertThat(c.finalComplexity()).isEqualTo(11);
        assertThat(c.spCosts()).containsExactly(10, 12);
        assertThat(c.issues()).isEmpty();
    }

    @Test
    @DisplayName("F3a — §20: Perdida gasta max(0, 2 + bruta) e tem teto 10 no Iniciante")
    void lostArtCost() {
        AssemblyCheck c = AssemblyRules.checkTechnique(ArtCategory.LOST, technique(Graduation.BEGINNER, List.of(
                variation(TechniqueCore.STRIKE, item("Sequência", MODULE, 3), item("Massa", MODULE, 2),
                        item("Montinho", MODULE, 1)))));

        assertThat(c.spCosts()).containsExactly(8);
        assertThat(c.ceiling()).isEqualTo(10);
    }

    @Test
    @DisplayName("F3a — acima do teto da Arte Criada gera issue")
    void createdCeiling() {
        AssemblyCheck c = AssemblyRules.checkTechnique(ArtCategory.CREATED, technique(Graduation.BEGINNER, List.of(
                variation(TechniqueCore.STRIKE, item("Contragolpe", MODULE, 3), item("Sequência", MODULE, 3),
                        item("Montinho", MODULE, 1)))));

        assertThat(c.ceiling()).isEqualTo(6);
        assertThat(c.issues()).anyMatch(s -> s.contains("teto"));
    }

    @Test
    @DisplayName("F3a — no máximo 2 Efeitos por variação, salvo Pés no Ar")
    void effectLimit() {
        AssemblyVariation v = variation(TechniqueCore.CONTROL, item("Empurrar", EFFECT, 1), item("Derrubar", EFFECT, 2),
                item("Prender", EFFECT, 3));
        assertThat(AssemblyRules.checkTechnique(ArtCategory.CLASSIC, technique(Graduation.MASTER, List.of(v))).issues())
                .anyMatch(s -> s.contains("Efeitos"));

        v.getItems().add(item("Pés no Ar", UNIQUE, 4));
        assertThat(AssemblyRules.checkTechnique(ArtCategory.CLASSIC, technique(Graduation.MASTER, List.of(v))).issues())
                .noneMatch(s -> s.contains("Efeitos"));
    }

    @Test
    @DisplayName("F3a — RN-TEC-05: dependências de módulo")
    void dependencies() {
        List<String> issues = AssemblyRules.checkTechnique(ArtCategory.CLASSIC, technique(Graduation.BEGINNER, List.of(
                variation(TechniqueCore.STRIKE, item("Ímpeto", MODULE, 3), item("Sufocar", EFFECT, 3),
                        item("Paralisar", EFFECT, 6))))).issues();

        assertThat(issues).anyMatch(s -> s.contains("Ímpeto") && s.contains("Investida"));
        assertThat(issues).anyMatch(s -> s.contains("Sufocar"));
        assertThat(issues).anyMatch(s -> s.contains("Paralisar"));
    }

    @Test
    @DisplayName("F3a — RN-BASE-01: Fudo Dachi = 8/5, PT 5, teto de base clássica Iniciante 5, sem gasto")
    void fudoDachi() {
        ArtBase b = new ArtBase();
        b.setGraduation(Graduation.BEGINNER);
        AssemblyItem rooted = item("Enraizado", MODULE, 2);
        rooted.setNonAffine(true);
        b.setItems(new ArrayList<>(List.of(item("Rejeitar o Impacto", UNIQUE, 3), item("Guarda", MODULE, 3), rooted,
                item("Penalidade", LIMITATION, -2), item("Pesada", LIMITATION, -1))));

        AssemblyCheck c = AssemblyRules.checkBase(ArtCategory.CLASSIC, b);

        assertThat(c.rawComplexity()).isEqualTo(8);
        assertThat(c.finalComplexity()).isEqualTo(5);
        assertThat(c.trainingPoints()).isEqualTo(5);
        assertThat(c.ceiling()).isEqualTo(5);
        assertThat(c.spCosts()).isEmpty();
        assertThat(c.issues()).isEmpty();
    }
}
