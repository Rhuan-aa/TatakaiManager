package com.tatakai.manager.timeskip.exception;

import com.tatakai.manager.timeskip.entity.TimeSkip;

public class TimeSkipClosedException extends RuntimeException {
    public TimeSkipClosedException() {
        super("Este TimeSkip já está encerrado");
    }
}
