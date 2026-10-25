package com.tatakai.manager.npc.exception;

public class NpcNotFoundException extends RuntimeException {
    public NpcNotFoundException() {
        super("NPC não encontrado");
    }
}
