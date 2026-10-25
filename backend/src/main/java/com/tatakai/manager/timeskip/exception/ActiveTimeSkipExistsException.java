package com.tatakai.manager.timeskip.exception;

import com.tatakai.manager.timeskip.entity.TimeSkip;

public class ActiveTimeSkipExistsException extends RuntimeException {
    public ActiveTimeSkipExistsException() {
        super("Encerre o TimeSkip atual antes de criar um novo");
    }
}
