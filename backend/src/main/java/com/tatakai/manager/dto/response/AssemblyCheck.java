package com.tatakai.manager.dto.response;

import java.util.List;

/** Números derivados de uma montagem (técnica ou base) e problemas encontrados. `spCosts`: um por variação; vazio em base. */
public record AssemblyCheck(int rawComplexity, int finalComplexity, List<Integer> spCosts, int trainingPoints,
                            int ceiling, List<String> issues) {}
