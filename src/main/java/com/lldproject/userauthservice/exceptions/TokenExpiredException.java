package com.lldproject.userauthservice.exceptions;

public class TokenExpiredException extends RuntimeException {
    public TokenExpiredException(String s) {
        super(s);
    }
}
