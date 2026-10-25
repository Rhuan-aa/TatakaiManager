package com.tatakai.manager.sheet.exception;

public class SheetEntryNotFoundException extends RuntimeException {
    public SheetEntryNotFoundException() {
        super("Ferimento ou item não encontrado");
    }
}
