package com.tatakai.manager.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException() {
        super("Agendamento não encontrado");
    }
}
