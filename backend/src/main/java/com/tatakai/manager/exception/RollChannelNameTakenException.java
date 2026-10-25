package com.tatakai.manager.exception;

public class RollChannelNameTakenException extends RuntimeException {
    public RollChannelNameTakenException() {
        super("Já existe um canal com este nome na campanha");
    }
}
