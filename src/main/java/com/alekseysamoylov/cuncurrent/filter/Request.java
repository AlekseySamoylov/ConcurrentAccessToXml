package com.alekseysamoylov.cuncurrent.filter;

public class Request {
    private final String ipAddress;

    public Request(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
