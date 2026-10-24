package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.KeikenRank;

public record SheetProgress(int fightExperience, int pendingOvercomePoints, KeikenRank keiken, Integer nextKeikenAt,
                            int sparringExperience, int sparringCap, int barrierOvercomePoints, int instinctSlots) {}
