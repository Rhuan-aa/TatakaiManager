package com.tatakai.manager.exception;

/** Upload de imagem inválido (tipo não suportado, vazia ou grande demais). */
public class InvalidImageException extends RuntimeException {
    public InvalidImageException(String message) {
        super(message);
    }
}
