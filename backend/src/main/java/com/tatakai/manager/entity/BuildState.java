package com.tatakai.manager.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Estado mutável da Construção Inicial (jsonb em {@link CharacterBuild}). */
@Data
@NoArgsConstructor
public class BuildState {
    private Integer traitCountRoll;
    private List<TraitSlot> traitSlots = new ArrayList<>();
    private boolean traitsConfirmed;

    private Integer bloodTestRoll;
    private int clanSlots;
    private List<UUID> clanIds = new ArrayList<>();
    private List<Integer> clanRolls = new ArrayList<>();
    private boolean clansConfirmed;

    private List<Integer> attributeDice = new ArrayList<>();
    /** Atributo -> índice do dado em {@code attributeDice}. */
    private Map<Attribute, Integer> assignment = new EnumMap<>(Attribute.class);
    private boolean attributesConfirmed;

    private Map<Attribute, Integer> freePoints = new EnumMap<>(Attribute.class);
    private boolean freePointsConfirmed;
}
