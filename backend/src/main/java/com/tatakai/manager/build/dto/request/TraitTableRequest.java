package com.tatakai.manager.build.dto.request;

import com.tatakai.manager.catalog.entity.TraitRarity;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Tabela de rolagem do jogador para uma raridade; lista vazia volta ao padrão do catálogo. */
public record TraitTableRequest(@NotNull TraitRarity rarity, @NotNull List<UUID> traitIds) {}
