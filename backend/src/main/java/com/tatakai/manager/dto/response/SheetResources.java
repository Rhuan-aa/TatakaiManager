package com.tatakai.manager.dto.response;

public record SheetResources(int hpMax, int effectiveHpMax, int spMax, int kpMax,
        int currentHp, int currentSp, int currentKp, int openWounds) {}
