package com.tatakai.manager.catalog.service;

import com.tatakai.manager.sheet.entity.CharacterSheet;
import com.tatakai.manager.sheet.entity.ItemLocation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Catálogo estático de equipamentos, proteções e roupas (capítulo Equipamentos, plano §43). */
@Component
public class EquipmentCatalog {

    /**
     * kind: PROTECTION, COMPETITION, ACADEMY, TOOL, CLOTHING. protection: redução por tipo (BLUNT, SLASH, PIERCE,
     * FIRE, FIREARM; DEFAULT = demais tipos). cover: CABECA, TRONCO, BRACOS, PERNAS. category: LIGHT/MEDIUM/HEAVY.
     * effects: códigos com efeito automático (HELMET, BOXING_GLOVES, MMA_GLOVES, MOUTHGUARD, SHIN_GUARDS,
     * TRAINING_HELMET, KIMONO, COLD_GEAR, FIREPROOF).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Def(String key, String name, String kind, Map<String, Integer> protection, List<String> cover,
                      Double weightKg, String category, List<String> effects, String text, String price, String access,
                      Container container) {
        public boolean has(String effect) {
            return effects != null && effects.contains(effect);
        }
    }

    /**
     * Item que, vestido, abre espaços extras em QUICK (Acesso Rápido) ou PACK (Carregando): {@code slots} itens,
     * cada um com até {@code maxItemKg} e no total até {@code maxKg} (nulo = sem limite).
     */
    public record Container(String location, int slots, Double maxItemKg, Double maxKg) { }

    private List<Def> defs;

    public synchronized List<Def> all() {
        if (defs == null) {
            try (var in = new ClassPathResource("catalog/equipment.json").getInputStream()) {
                defs = List.copyOf(new ObjectMapper().readValue(in, new TypeReference<List<Def>>() { }));
            } catch (IOException e) {
                throw new IllegalStateException("catalog/equipment.json ilegível", e);
            }
        }
        return defs;
    }

    public Optional<Def> find(String key) {
        return key == null ? Optional.empty() : all().stream().filter(d -> d.key().equals(key)).findFirst();
    }

    /** O que o lutador está vestindo (itens com equipamento do catálogo em WORN). */
    public List<Def> worn(CharacterSheet s) {
        if (s == null || s.getInventory() == null) return List.of();
        return s.getInventory().stream()
                .filter(i -> i.getLocation() == ItemLocation.WORN && i.getEquipmentKey() != null && !i.isBroken())
                .map(i -> find(i.getEquipmentKey()).orElse(null)).filter(java.util.Objects::nonNull).toList();
    }

    public boolean wears(CharacterSheet s, String effect) {
        return worn(s).stream().anyMatch(d -> d.has(effect));
    }
}
