package com.linagora.tmail.carddav;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;

public record CardDavCreationObjectRequest(String version,
                                           String uid,
                                           Optional<String> fullName,
                                           Optional<List<String>> nameList,
                                           Email email) {

    public CardDavCreationObjectRequest {
        Preconditions.checkArgument(StringUtils.isEmpty(version), "Version should not be empty");
        Preconditions.checkArgument(StringUtils.isEmpty(uid), "Uid should not be empty");
        Preconditions.checkArgument(email != null, "Email should not be null");
    }

    public record Email(List<EmailType> type,
                        MailAddress value) {

        public Email {
            Preconditions.checkArgument(!type.isEmpty(), "Email type should not be empty");
            Preconditions.checkArgument(value != null, "Email value should not be null");
        }
    }

    public enum EmailType {
        WORK("Work"), HOME("Home"), OTHER("Other");

        private final String value;

        EmailType(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }
}
