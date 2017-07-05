package com.alekseysamoylov.cuncurrent.filter.exception;

public class BlackListCriticalException extends RuntimeException {

    public BlackListCriticalException(String message, Throwable e) {
        super(message, e);
    }
}
