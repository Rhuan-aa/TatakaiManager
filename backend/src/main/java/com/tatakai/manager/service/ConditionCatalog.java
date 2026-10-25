package com.tatakai.manager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tatakai.manager.entity.ConditionExpiry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Catálogo estático de condições (spec §4.1), lido de catalog/conditions.json. */
@Component
public class ConditionCatalog {

    /**
     * Condição não se acumula consigo mesma: reaplicar renova; com `levels` (Queimando, Envenenado), vale o nível mais
     * forte (intensidade 1..N). `stack` fica só por compatibilidade do JSON.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Def(String key, String name, String effect, String exit,
                      ConditionExpiry defaultExpiresAt, String stack, List<String> levels) {
        public boolean leveled() {
            return levels != null && !levels.isEmpty();
        }
    }

    private List<Def> defs;

    public synchronized List<Def> all() {
        if (defs == null) {
            try (var in = new ClassPathResource("catalog/conditions.json").getInputStream()) {
                defs = List.copyOf(new ObjectMapper().readValue(in, new TypeReference<List<Def>>() { }));
            } catch (IOException e) {
                throw new IllegalStateException("catalog/conditions.json ilegível", e);
            }
        }
        return defs;
    }

    public Optional<Def> find(String key) {
        return all().stream().filter(d -> d.key().equals(key)).findFirst();
    }
}
