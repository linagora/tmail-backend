package com.linagora.tmail.carddav;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;

import com.google.common.hash.Hashing;

import ezvcard.parameter.EmailType;

public class CardDavCreationFactory {
    private static final String VERSION = "4.0";
    private static final EmailType EMAIL_TYPE_DEFAULT = EmailType.WORK;

    public static CardDavCreationObjectRequest create(Optional<String> fullName, MailAddress email) {
        CardDavCreationObjectRequest.Email emailObject = new CardDavCreationObjectRequest.Email(List.of(EMAIL_TYPE_DEFAULT), email);
        return new CardDavCreationObjectRequest(VERSION, createContactUid(email), fullName, Optional.empty(), emailObject);
    }

    public static CardDavCreationObjectRequest create(MailAddress email) {
        return create(Optional.empty(), email);
    }

    public static String createContactUid(MailAddress email) {
        return Hashing.sha1()
            .hashString(email.asString(), StandardCharsets.UTF_8)
            .toString();
    }
}
