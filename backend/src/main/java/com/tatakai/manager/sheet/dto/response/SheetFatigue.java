package com.tatakai.manager.sheet.dto.response;

import com.tatakai.manager.sheet.entity.DoseEntry;
import com.tatakai.manager.sheet.entity.FatigueBand;

import java.util.List;

public record SheetFatigue(int physical, FatigueBand physicalBand, int mental, FatigueBand mentalBand,
                           List<DoseEntry> doses) {}  // doses desde o último Repouso (§54)
