package com.linagora.tmail.webadmin.model;

import java.util.UUID;

public record EmailAddressContactIdResponse(String id) {
    public static EmailAddressContactIdResponse from(UUID id) {
        return new EmailAddressContactIdResponse(id.toString());
    }
}
