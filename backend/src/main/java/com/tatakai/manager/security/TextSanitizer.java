package com.tatakai.manager.security;

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
        return input == null ? null : POLICY.sanitize(input);
    }
}
