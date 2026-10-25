package com.tatakai.manager.timeskip.exception;

import com.tatakai.manager.timeskip.entity.TimeSkip;

public class TimeSkipNotFoundException extends RuntimeException {
    public TimeSkipNotFoundException() {
        super("TimeSkip não encontrado");
    }
}
