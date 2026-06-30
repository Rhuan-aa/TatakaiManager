package com.tatakai.manager.exception;

public class SlotTakenException extends RuntimeException {
    public SlotTakenException() {
        super("Slot já ocupado");
    }
}
