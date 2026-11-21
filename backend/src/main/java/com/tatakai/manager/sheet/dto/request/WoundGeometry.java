package com.tatakai.manager.sheet.dto.request;

import java.util.List;

/**
 * Forma da marca, normalizada 0..1 na caixa da parte do corpo: um ponto + raio (mancha, furo) ou um traço
 * (corte, cicatriz). Conferida por {@code WoundRules.validate}.
 */
public record WoundGeometry(List<List<Double>> points, Double r) {}
