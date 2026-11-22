package com.tatakai.manager.shared.security;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Sanitiza texto livre antes de persistir (NFR-02 — proteção contra XSS).
 * A política não permite nenhuma tag: remove HTML/scripts mantendo o texto.
 */
@Component
public class TextSanitizer {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder().toFactory();

    public String sanitize(String input) {
        if (input == null) return null;
        // O OWASP devolve HTML escapado; o front mostra como texto (React escapa de novo), então "&#34;" aparecia na tela.
        // Desfaz só o que é inofensivo; < e > continuam escapados. &amp; por último, para não decodificar duas vezes.
        return POLICY.sanitize(input)
                .replace("&#34;", "\"").replace("&#39;", "'").replace("&#43;", "+").replace("&#61;", "=")
                .replace("&#64;", "@").replace("&#96;", "`").replace("&amp;", "&");
    }
}
