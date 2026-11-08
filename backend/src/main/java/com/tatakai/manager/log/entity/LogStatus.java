package com.tatakai.manager.log.entity;

/** Situação de publicação de um log: pedido de jogador fica PENDING até o Mestre revisar. */
public enum LogStatus {
    PUBLISHED, PENDING, REJECTED
}
