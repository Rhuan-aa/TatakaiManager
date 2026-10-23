package com.tatakai.manager.exception;

public class NpcSheetAlreadyExistsException extends RuntimeException {
    public NpcSheetAlreadyExistsException() {
        super("Este NPC já tem ficha");
    }
}
