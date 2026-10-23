package com.tatakai.manager.exception;

public class CharacterSheetNotFoundException extends RuntimeException {
    public CharacterSheetNotFoundException() {
        super("Ficha não encontrada");
    }
}
