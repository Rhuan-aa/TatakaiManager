package com.tatakai.manager.exception;

public class ActiveTimeSkipExistsException extends RuntimeException {
    public ActiveTimeSkipExistsException() {
        super("Encerre o TimeSkip atual antes de criar um novo");
    }
}
