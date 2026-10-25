package com.tatakai.manager.timeskip.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException() {
        super("Agendamento não encontrado");
    }
}
