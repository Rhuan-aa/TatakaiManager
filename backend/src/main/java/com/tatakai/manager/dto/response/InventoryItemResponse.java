package com.tatakai.manager.dto.response;

import java.util.UUID;

public record InventoryItemResponse(UUID id, String name, int quantity, String description, double weightKg) {}
