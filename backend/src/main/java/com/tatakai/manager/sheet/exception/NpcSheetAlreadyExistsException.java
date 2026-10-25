package com.tatakai.manager.sheet.exception;

public class NpcSheetAlreadyExistsException extends RuntimeException {
    public NpcSheetAlreadyExistsException() {
        super("Este NPC já tem ficha");
    }
}
