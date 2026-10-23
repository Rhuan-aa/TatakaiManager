package com.tatakai.manager.exception;

public class SheetEntryNotFoundException extends RuntimeException {
    public SheetEntryNotFoundException() {
        super("Ferimento ou item não encontrado");
    }
}
