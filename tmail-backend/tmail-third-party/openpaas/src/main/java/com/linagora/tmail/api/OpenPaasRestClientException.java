package com.linagora.tmail.api;

public class OpenPaasRestClientException extends RuntimeException {

    public OpenPaasRestClientException(String message) {
        super(message);
    }

    public OpenPaasRestClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
