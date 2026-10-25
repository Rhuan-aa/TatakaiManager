package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.entity.FatigueBand;

public record SheetFatigue(int physical, FatigueBand physicalBand, int mental, FatigueBand mentalBand) {}
