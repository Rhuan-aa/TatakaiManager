package com.tatakai.manager.timeskip.exception;

public class SlotTakenException extends RuntimeException {
    public SlotTakenException() {
        super("Slot já ocupado");
    }
}
