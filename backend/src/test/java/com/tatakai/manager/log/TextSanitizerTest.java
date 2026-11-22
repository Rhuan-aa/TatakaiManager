package com.tatakai.manager.log;

import com.tatakai.manager.shared.security.TextSanitizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TextSanitizer — NFR-02 (proteção contra XSS)")
class TextSanitizerTest {

    private final TextSanitizer sanitizer = new TextSanitizer();

    @Test
    @DisplayName("remove script malicioso do texto")
    void sanitize_stripsScript() {
        String result = sanitizer.sanitize("<script>alert('xss')</script>");

        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("<");
    }

    @Test
    @DisplayName("remove tags HTML mantendo o texto visível")
    void sanitize_stripsTagsKeepsText() {
        String result = sanitizer.sanitize("Treinei <b>esgrima</b> com Aldric");

        assertThat(result).contains("Treinei");
        assertThat(result).contains("esgrima");
        assertThat(result).contains("Aldric");
        assertThat(result).doesNotContain("<b>");
    }

    @Test
    @DisplayName("texto puro permanece inalterado")
    void sanitize_plainTextUnchanged() {
        String result = sanitizer.sanitize("Aprendi infiltração com Taryn durante a semana");

        assertThat(result).isEqualTo("Aprendi infiltração com Taryn durante a semana");
    }

    @Test
    @DisplayName("aspas, apóstrofo e & ficam como texto (sem virar entidade HTML); < e > seguem escapados")
    void sanitize_keepsQuotesAsText() {
        String result = sanitizer.sanitize("Ele disse: \"De novo\" & foi d'água <b>já</b> 3 < 5");

        assertThat(result).isEqualTo("Ele disse: \"De novo\" & foi d'água já 3 &lt; 5");
    }
}
