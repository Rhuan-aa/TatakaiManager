package com.tatakai.manager.roll.exception;

public class RollChannelNameTakenException extends RuntimeException {
    public RollChannelNameTakenException() {
        super("Já existe um canal com este nome na campanha");
    }
}
