package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.FatigueBand;

public record SheetFatigue(int physical, FatigueBand physicalBand, int mental, FatigueBand mentalBand) {}
