package com.tatakai.manager.exception;

public class SheetLockedException extends RuntimeException {
    public SheetLockedException(String message) {
        super(message);
    }
}
