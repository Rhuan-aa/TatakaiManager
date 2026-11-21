package com.tatakai.manager.sheet.service.rules;

import com.tatakai.manager.sheet.dto.request.WoundGeometry;
import com.tatakai.manager.sheet.entity.WoundType;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;

import java.util.List;

/** Regras puras da marca desenhada (§53.3): formato da geometria por tipo. */
public final class WoundRules {

    public static final int MAX_POINTS = 200;
    public static final double MAX_RADIUS = 0.5;

    private WoundRules() {}

    public static void validate(WoundType type, WoundGeometry g) {
        if (g == null || g.points() == null || g.points().isEmpty()) throw invalid("Desenhe o ferimento");
        if (g.points().size() > MAX_POINTS) throw invalid("Traço longo demais");
        for (List<Double> p : g.points()) {
            if (p == null || p.size() != 2 || p.get(0) == null || p.get(1) == null) throw invalid("Ponto inválido");
            if (p.get(0) < 0 || p.get(0) > 1 || p.get(1) < 0 || p.get(1) > 1) throw invalid("Ponto fora da parte do corpo");
        }
        boolean spot = type == WoundType.IMPACT || type == WoundType.HOLE;
        if (spot) {
            if (g.points().size() != 1) throw invalid("Mancha e furo usam um ponto só");
            if (g.r() == null || g.r() <= 0 || g.r() > MAX_RADIUS) throw invalid("Tamanho inválido");
        } else if (g.points().size() < 2) {
            throw invalid("Corte e cicatriz precisam de um traço");
        }
    }

    private static InvalidCharacterSheetException invalid(String msg) {
        return new InvalidCharacterSheetException(msg);
    }
}
