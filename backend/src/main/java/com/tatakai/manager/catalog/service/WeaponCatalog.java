package com.tatakai.manager.catalog.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Catálogo estático de armas (capítulo Armas, plano §42), lido de catalog/weapons.json. */
@Component
public class WeaponCatalog {

    /**
     * Arma do livro. group: IMPROVISED, ACADEMY, MELEE, AUGMENT, THROWN, RANGED, FIREARM. damageType: BLUNT, SLASH,
     * PIERCE, FIRE. reload: FAST, NORMAL, SLOW. `alt` = chave do outro modo (Tufa). `hidden` = só existe como modo.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Def(String key, String name, String group, String weaponClass, String dice, String diceTwoHands,
                      String damageType, String reach, Double rangeM, Double throwM, Integer capacity, boolean light,
                      Integer heavyStr, boolean large, boolean compact, boolean small, boolean defensive, int impact,
                      boolean flexible, boolean fragile, String breaksInto, boolean entangle, boolean returning,
                      boolean dispersion, Double explosiveRadius, boolean incendiary, boolean smoke, String reload,
                      boolean slowCock, Integer burst, Integer unstable, Integer bonus, String alt, boolean twoHands,
                      boolean throwOnly, boolean selfWound, boolean hidden, String price, String access,
                      /** Chave do consumível de munição (§50.2); null = não usa munição. */
                      String ammo) {

        public boolean firearm() { return "FIREARM".equals(group); }
        public boolean augment() { return "AUGMENT".equals(group); }
        public boolean thrownWeapon() { return "THROWN".equals(group); }
        public boolean shooter() { return "RANGED".equals(group) || firearm(); }
        public boolean area() { return explosiveRadius != null; }
        public boolean variable() { return diceTwoHands != null; }
        /** Pesada ou Grande, disparo/fogo não Compacta e as improvisadas "duas mãos" ocupam as duas mãos. */
        public boolean forcesTwoHands() { return twoHands || heavyStr != null || large || (shooter() && !compact); }

        /** Carga somada ao Gasto de Uso das técnicas armadas: Leve 3, Pesada/Grande 8, demais 5. */
        public int load() { return light ? 3 : heavyStr != null || large ? 8 : 5; }

        /** Tipo marcial da arma: fogo = Pólvora, disparo/arremesso = Artesanais, senão pelo tipo de dano. */
        public String martialType() {
            if (firearm()) return "Pólvora";
            if (shooter() || thrownWeapon()) return "Artesanais";
            return switch (damageType == null ? "" : damageType) {
                case "SLASH" -> "Lâmina";
                case "PIERCE" -> "Perfurante";
                default -> "Contundente";
            };
        }
    }

    private List<Def> defs;

    public synchronized List<Def> all() {
        if (defs == null) {
            try (var in = new ClassPathResource("catalog/weapons.json").getInputStream()) {
                defs = List.copyOf(new ObjectMapper().readValue(in, new TypeReference<List<Def>>() { }));
            } catch (IOException e) {
                throw new IllegalStateException("catalog/weapons.json ilegível", e);
            }
        }
        return defs;
    }

    public Optional<Def> find(String key) {
        return key == null ? Optional.empty() : all().stream().filter(d -> d.key().equals(key)).findFirst();
    }
}
