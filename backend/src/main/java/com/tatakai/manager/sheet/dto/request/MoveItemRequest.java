package com.tatakai.manager.sheet.dto.request;

import com.tatakai.manager.sheet.entity.ItemLocation;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Mover um item: local, contêiner opcional (QUICK/PACK) e, para trocar de lugar, o item que está lá. */
public record MoveItemRequest(@NotNull ItemLocation location, UUID containerId, UUID swapWithId) {}
