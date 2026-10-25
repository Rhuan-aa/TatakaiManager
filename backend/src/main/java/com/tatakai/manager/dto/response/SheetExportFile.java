package com.tatakai.manager.dto.response;

import com.tatakai.manager.entity.ArtCategory;
import com.tatakai.manager.entity.Attribute;
import com.tatakai.manager.entity.BodyType;
import com.tatakai.manager.entity.BuildGains;
import com.tatakai.manager.entity.CharacterArtStatus;
import com.tatakai.manager.entity.Dominance;
import com.tatakai.manager.entity.Graduation;
import com.tatakai.manager.entity.InjurySeverity;
import com.tatakai.manager.entity.InjuryType;
import com.tatakai.manager.entity.KeikenRank;
import com.tatakai.manager.entity.Mastery;
import com.tatakai.manager.entity.MasteryBonus;
import com.tatakai.manager.entity.MoveKind;
import com.tatakai.manager.entity.TechniqueState;
import com.tatakai.manager.entity.TraitAcquisition;
import com.tatakai.manager.entity.TraitCategory;
import com.tatakai.manager.entity.TraitRarity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arquivo {@code .tatakai.json} (F7, PLANO §21.1). Campos de seção oculta ficam {@code null}.
 * Derivados (modificadores, faixas, limitadores) nunca entram: são recalculados.
 */
public record SheetExportFile(
        int schemaVersion,
        Instant exportedAt,
        ExportedBy exportedBy,
        Source source,
        SheetData sheet,
        List<TraitData> traits,
        List<ClanData> clans,
        List<ArtData> arts,
        List<TechniqueData> looseTechniques,
        List<InstinctData> instincts,
        List<RivalData> rivals
) {
    public static final int SCHEMA_VERSION = 1;

    public record ExportedBy(UUID userId, String name) {}

    public record Source(UUID campaignId, UUID sheetId, UUID npcId) {}

    public record SheetData(
            String name, BodyType bodyType, Dominance dominance, Double heightM, Double weightKg,
            Map<Attribute, Integer> attributes,
            Integer hpMax, Integer spMax, Integer kpMax, Integer currentHp, Integer currentSp, Integer currentKp,
            Integer openWounds, Integer physicalFatigue, Integer mentalFatigue,
            Integer fightExperience, Integer sparringExperience, Integer pendingOvercomePoints,
            Integer barrierOvercomePoints,
            Map<String, Integer> limiterAdjustments, Map<String, Integer> limiterCarry,
            Integer money, List<ItemData> inventory,
            List<InjuryData> injuries) {}

    public record ItemData(String name, int quantity, String description, Double weightKg) {}

    public record InjuryData(InjuryType type, String region, InjurySeverity severity, int hpMaxReduction,
                             int limiterReduction, Attribute affectedAttribute, boolean treated) {}

    public record TraitData(UUID traitId, int traitVersion, String name, TraitRarity rarity, TraitCategory category,
                            String description, BuildGains buildGains, TraitAcquisition acquiredIn,
                            boolean gainsApplied) {}

    public record ClanData(UUID clanId, int clanVersion, String name, BuildGains buildGains) {}

    public record ArtData(UUID artId, String artName, ArtCategory category, Graduation graduation,
                          CharacterArtStatus status, int graduationPoints, List<TechniqueData> techniques) {}

    public record TechniqueData(MoveKind kind, String name, Graduation graduation, int complexity,
                                int trainingPoints, int trainingProgress, TechniqueState state, Mastery mastery,
                                int masteryPoints, List<MasteryBonus> bonuses, String borrowedFrom) {}

    public record InstinctData(UUID instinctId, String name, KeikenRank rank, String description) {}

    public record RivalData(String name, String art, int fights, int readBonus, List<String> knownTechniques) {}
}
