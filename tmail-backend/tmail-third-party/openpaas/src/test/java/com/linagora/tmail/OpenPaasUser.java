package com.linagora.tmail;

import org.bson.Document;

public record OpenPaasUser(String id, String firstname, String lastname, String email, String password) {

    public static OpenPaasUser fromDocument(Document document) {
        return new OpenPaasUser(
            document.getObjectId("_id").toString(),
            document.getString("firstname"),
            document.getString("lastname"),
            document.getList("accounts", Document.class)
                .getFirst().getList("emails", String.class).getFirst(),
            document.getString("password"));
    }
}
