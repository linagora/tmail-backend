package com.linagora.tmail.webadmin.model;

import java.util.UUID;

public class EmailAddressContactIdResponse {
    private final String id;

    public static EmailAddressContactIdResponse from(UUID id) {
        return new EmailAddressContactIdResponse(id.toString());
    }

    public EmailAddressContactIdResponse(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
