package com.tatakai.manager.entity;

/** Snapshot das regras da campanha no início da Construção Inicial (divineMode null = desligado). */
public record BuildSettings(String traitMethod, String divineMode, boolean randomClan,
                            boolean randomAttributes, boolean gmRerolls) {
}
