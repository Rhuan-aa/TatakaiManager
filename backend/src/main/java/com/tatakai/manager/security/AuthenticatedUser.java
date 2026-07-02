package com.tatakai.manager.security;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Acessa o ID do usuário autenticado a partir do SecurityContext.
 * O principal é definido pelo {@link JwtAuthenticationFilter} como o subject (UUID) do JWT.
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {}

    public static UUID id() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(principal.toString());
    }
}
