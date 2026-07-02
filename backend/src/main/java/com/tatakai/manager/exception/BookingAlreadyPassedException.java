package com.tatakai.manager.exception;

public class BookingAlreadyPassedException extends RuntimeException {
    public BookingAlreadyPassedException() {
        super("Não é possível alterar um agendamento de um dia que já passou");
    }
}
