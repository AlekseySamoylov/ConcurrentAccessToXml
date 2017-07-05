package com.alekseysamoylov.cuncurrent.utils;

import java.util.ResourceBundle;

public class MessageSourceHelper {

    private static final ResourceBundle MESSAGE_SOURCE = ResourceBundle.getBundle("messages");

    public static String getMessage(String code) {
        return MESSAGE_SOURCE.getString(code);
    }


}
