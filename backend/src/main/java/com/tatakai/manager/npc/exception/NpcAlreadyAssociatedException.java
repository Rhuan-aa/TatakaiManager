package com.tatakai.manager.npc.exception;

public class NpcAlreadyAssociatedException extends RuntimeException {
    public NpcAlreadyAssociatedException() {
        super("NPC já associado a esta campanha");
    }
}
