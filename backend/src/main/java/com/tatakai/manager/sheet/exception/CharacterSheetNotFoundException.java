package com.tatakai.manager.sheet.exception;

public class CharacterSheetNotFoundException extends RuntimeException {
    public CharacterSheetNotFoundException() {
        super("Ficha não encontrada");
    }
}
