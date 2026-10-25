package com.tatakai.manager.sheet.exception;

public class SheetLockedException extends RuntimeException {
    public SheetLockedException(String message) {
        super(message);
    }
}
