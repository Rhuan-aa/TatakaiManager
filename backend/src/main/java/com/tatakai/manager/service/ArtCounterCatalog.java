package com.tatakai.manager.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Catálogo estático de contadores de arte (spec §8.2), lido de catalog/art_counters.json. */
@Component
public class ArtCounterCatalog {

    /** sameArt: o incremento só vale se o golpe for de técnica da arte do contador. */
    public record Increment(String event, int delta, boolean sameArt) { }

    public record Reset(String event, int value) { }

    public record Def(String key, String art, String name, int min, int max, boolean perTarget,
                      List<Increment> increments, List<Reset> resets, String note) { }

    private List<Def> defs;

    public synchronized List<Def> all() {
        if (defs == null) {
            try (var in = new ClassPathResource("catalog/art_counters.json").getInputStream()) {
                defs = List.copyOf(new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .readValue(in, new TypeReference<List<Def>>() { }));
            } catch (IOException e) {
                throw new IllegalStateException("catalog/art_counters.json ilegível", e);
            }
        }
        return defs;
    }

    public Optional<Def> find(String key) {
        return all().stream().filter(d -> d.key().equals(key)).findFirst();
    }
}
