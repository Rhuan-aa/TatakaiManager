package com.tatakai.manager.progression.dto.response;

import com.tatakai.manager.progression.entity.KeikenRank;

public record SheetProgress(int fightExperience, int pendingOvercomePoints, KeikenRank keiken, Integer nextKeikenAt,
                            int sparringExperience, int sparringCap, int barrierOvercomePoints, int instinctSlots) {}
