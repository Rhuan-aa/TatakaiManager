package com.tatakai.manager.build.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** O jogador escolhe o traço de um slot pendente (repetido, fora da tabela ou Lendário 20). */
public record PickTraitRequest(@NotNull UUID traitId) {}
