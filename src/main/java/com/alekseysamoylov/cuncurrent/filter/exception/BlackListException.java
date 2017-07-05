package com.alekseysamoylov.cuncurrent.filter.exception;

public class BlackListException extends Exception {

    public BlackListException(String message, Exception e) {
        super(message);
    }

    public BlackListException(String s) {
        super(s);
    }
}
