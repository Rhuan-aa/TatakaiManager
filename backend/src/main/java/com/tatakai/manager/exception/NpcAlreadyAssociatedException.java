package com.tatakai.manager.exception;

public class NpcAlreadyAssociatedException extends RuntimeException {
    public NpcAlreadyAssociatedException() {
        super("NPC já associado a esta campanha");
    }
}
