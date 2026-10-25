package com.tatakai.manager.timeskip.exception;

public class BookingAlreadyPassedException extends RuntimeException {
    public BookingAlreadyPassedException() {
        super("Não é possível alterar um agendamento de um dia que já passou");
    }
}
