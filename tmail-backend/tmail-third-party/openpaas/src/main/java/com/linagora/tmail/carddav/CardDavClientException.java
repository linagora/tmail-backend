package com.linagora.tmail.carddav;

public class CardDavClientException extends RuntimeException {

    public CardDavClientException(String message) {
        super(message);
    }

    public CardDavClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
