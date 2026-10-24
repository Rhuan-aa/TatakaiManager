package com.tatakai.manager.dto.request;

/** duelWon: o Mestre confirma o duelo pelo Critério de Vitória (só para graduar a Mestre). */
public record GraduateRequest(Boolean duelWon) {}
