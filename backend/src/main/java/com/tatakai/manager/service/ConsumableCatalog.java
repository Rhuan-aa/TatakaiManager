package com.tatakai.manager.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Catálogo estático de consumíveis (capítulo Consumíveis, plano §44). */
@Component
public class ConsumableCatalog {

    /**
     * use: SELF (Ação Menor em si), ALLY (Ação Maior em aliado), OFFENSIVE (Ação Menor ofensiva num alvo), NONE (fora de
     * combate). effect: STANCH, STANCH_HEAL, WAKE_KP, SP_1D6, GRIP_CHALK, PEPPER, STIMULANT; null = o Mestre aplica.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Def(String key, String name, String group, String use, String effect, String text, boolean dose,
                      String price, String access) {}

    private List<Def> defs;

    public synchronized List<Def> all() {
        if (defs == null) {
            try (var in = new ClassPathResource("catalog/consumables.json").getInputStream()) {
                defs = List.copyOf(new ObjectMapper().readValue(in, new TypeReference<List<Def>>() { }));
            } catch (IOException e) {
                throw new IllegalStateException("catalog/consumables.json ilegível", e);
            }
        }
        return defs;
    }

    public Optional<Def> find(String key) {
        return key == null ? Optional.empty() : all().stream().filter(d -> d.key().equals(key)).findFirst();
    }
}
