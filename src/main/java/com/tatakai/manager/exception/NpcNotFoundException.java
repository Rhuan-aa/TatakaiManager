package com.tatakai.manager.exception;

public class NpcNotFoundException extends RuntimeException {
    public NpcNotFoundException() {
        super("NPC não encontrado");
    }
}
