package com.tatakai.manager.timeskip.exception;

import com.tatakai.manager.timeskip.entity.TimeSkip;

public class InvalidActivityNameException extends RuntimeException {
    public InvalidActivityNameException() {
        super("Já existe uma atividade com esse nome neste TimeSkip");
    }
}
