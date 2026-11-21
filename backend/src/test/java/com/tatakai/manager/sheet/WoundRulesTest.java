package com.tatakai.manager.sheet;

import com.tatakai.manager.sheet.dto.request.WoundGeometry;
import com.tatakai.manager.sheet.entity.WoundType;
import com.tatakai.manager.sheet.exception.InvalidCharacterSheetException;
import com.tatakai.manager.sheet.service.rules.WoundRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WoundRules — §53.3 ferimentos desenhados no boneco")
class WoundRulesTest {

    private static WoundGeometry geo(Double r, double... xy) {
        List<List<Double>> pts = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) pts.add(List.of(xy[i], xy[i + 1]));
        return new WoundGeometry(pts, r);
    }

    @Test
    @DisplayName("mancha e furo: um ponto e raio entre 0 e 0,5")
    void spotShapes() {
        assertThatCode(() -> WoundRules.validate(WoundType.IMPACT, geo(0.2, 0.5, 0.5))).doesNotThrowAnyException();
        assertThatCode(() -> WoundRules.validate(WoundType.HOLE, geo(0.5, 0, 1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> WoundRules.validate(WoundType.HOLE, geo(null, 0.5, 0.5)))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> WoundRules.validate(WoundType.IMPACT, geo(0.6, 0.5, 0.5)))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> WoundRules.validate(WoundType.IMPACT, geo(0.2, 0.1, 0.1, 0.2, 0.2)))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }

    @Test
    @DisplayName("corte e cicatriz: traço de 2 a 200 pontos")
    void strokeShapes() {
        assertThatCode(() -> WoundRules.validate(WoundType.CUT, geo(null, 0.1, 0.1, 0.9, 0.8))).doesNotThrowAnyException();
        assertThatThrownBy(() -> WoundRules.validate(WoundType.SCAR, geo(null, 0.1, 0.1)))
                .isInstanceOf(InvalidCharacterSheetException.class);
        double[] many = new double[402];
        assertThatThrownBy(() -> WoundRules.validate(WoundType.CUT, geo(null, many)))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }

    @Test
    @DisplayName("coordenadas fora de 0..1 ou incompletas são inválidas")
    void coordinates() {
        assertThatThrownBy(() -> WoundRules.validate(WoundType.CUT, geo(null, 0.1, 1.2, 0.3, 0.3)))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> WoundRules.validate(WoundType.CUT,
                new WoundGeometry(List.of(List.of(0.1), List.of(0.2, 0.2)), null)))
                .isInstanceOf(InvalidCharacterSheetException.class);
        assertThatThrownBy(() -> WoundRules.validate(WoundType.CUT, null))
                .isInstanceOf(InvalidCharacterSheetException.class);
    }
}
