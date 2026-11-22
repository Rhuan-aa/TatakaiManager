package com.tatakai.manager.sheet.service.rules;

import com.tatakai.manager.sheet.entity.DoseEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Regra de Dose e Colaterais adiados (Consumíveis, §54). O "dia" da Dose vai até o próximo Repouso.
 * Repetir o mesmo consumível de Dose no dia dobra o Colateral daquela dose.
 */
public final class DoseRules {

    public static final String ANALGESICO = "ANALGESICO";
    public static final String CALMANTE = "CALMANTE";
    public static final String POMADA = "POMADA";
    public static final String TERMOGENICO = "TERMOGENICO";
    public static final String ENERGETICO = "ENERGETICO";
    public static final String KIT_CUTMAN = "KIT_CUTMAN";

    /** Consumíveis com uso pela ficha (fora de combate). */
    public static final Set<String> SHEET_USE = Set.of(ANALGESICO, CALMANTE, POMADA, TERMOGENICO, ENERGETICO, KIT_CUTMAN);
    public static final Set<String> DOSES = Set.of(ANALGESICO, CALMANTE, POMADA, TERMOGENICO, ENERGETICO);

    private DoseRules() {
    }

    public static int count(List<DoseEntry> doses, String key) {
        return doses == null ? 0 : doses.stream().filter(d -> d.key().equals(key)).mapToInt(DoseEntry::count).sum();
    }

    /** Multiplicador do Colateral da dose que vai ser tomada agora (2 se já houve dose igual no dia). */
    public static int factor(List<DoseEntry> doses, String key) {
        return count(doses, key) > 0 ? 2 : 1;
    }

    /** Registra mais uma dose; `masked` soma ao que o Analgésico já escondia. */
    public static List<DoseEntry> take(List<DoseEntry> doses, String key, int masked) {
        List<DoseEntry> out = new ArrayList<>();
        boolean found = false;
        for (DoseEntry d : doses == null ? List.<DoseEntry>of() : doses) {
            if (d.key().equals(key)) {
                out.add(new DoseEntry(key, d.count() + 1, d.masked() + masked, d.spent()));
                found = true;
            } else {
                out.add(d);
            }
        }
        if (!found) out.add(new DoseEntry(key, 1, masked, false));
        return out;
    }

    /**
     * O que o Repouso cobra/devolve: `masked` (Desgaste Físico escondido, volta antes da recuperação), `collateral`
     * (Desgaste Físico somado depois dela) e `mentalRecovery` (Desgaste Mental removido a mais).
     */
    public record RestEffect(int masked, int collateral, int mentalRecovery) {
    }

    public static RestEffect onRest(List<DoseEntry> doses) {
        if (doses == null) return new RestEffect(0, 0, 0);
        int masked = 0;
        int collateral = 0;
        for (DoseEntry d : doses) {
            masked += d.masked();
            if (ANALGESICO.equals(d.key()) || ENERGETICO.equals(d.key())) collateral += collateral(d.count());
        }
        return new RestEffect(masked, collateral, count(doses, CALMANTE) > 0 ? 1 : 0);
    }

    /** Colateral de 1 por dose, dobrado a partir da 2ª no dia: 1, 3, 5... */
    static int collateral(int count) {
        return count <= 0 ? 0 : 2 * count - 1;
    }

    /** Calmante e Termogênico: desvantagem em Iniciativa até o fim do dia. */
    public static boolean initiativeDisadvantage(List<DoseEntry> doses) {
        return count(doses, CALMANTE) > 0 || count(doses, TERMOGENICO) > 0;
    }

    /** Energético ainda não usado num combate do dia: +1 Iniciativa e +1 Adrenalina inicial. */
    public static boolean energized(List<DoseEntry> doses) {
        return doses != null && doses.stream().anyMatch(d -> ENERGETICO.equals(d.key()) && !d.spent());
    }

    public static List<DoseEntry> spendEnergy(List<DoseEntry> doses) {
        return doses.stream().map(d -> ENERGETICO.equals(d.key())
                ? new DoseEntry(d.key(), d.count(), d.masked(), true) : d).toList();
    }
}
