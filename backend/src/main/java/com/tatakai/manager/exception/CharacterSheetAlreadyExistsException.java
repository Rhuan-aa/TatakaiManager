package com.tatakai.manager.exception;

public class CharacterSheetAlreadyExistsException extends RuntimeException {
    public CharacterSheetAlreadyExistsException() {
        super("Você já tem uma ficha nesta campanha");
    }
}
