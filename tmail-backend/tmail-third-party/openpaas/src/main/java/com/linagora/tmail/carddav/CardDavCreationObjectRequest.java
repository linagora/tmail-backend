package com.linagora.tmail.carddav;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        Preconditions.checkArgument(StringUtils.isNotEmpty(version), "Version should not be empty");
        Preconditions.checkArgument(StringUtils.isNotEmpty(uid), "Uid should not be empty");
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

    /**
     * @see <a href="https://tools.ietf.org/html/rfc6350">RFC 6350</a>
     */
    public String toVCard() {
        return Stream.of(
                "BEGIN:VCARD",
                "VERSION:" + version,
                fullName.map(fn -> "FN:" + fn).orElse(null),
                nameList.map(this::formatNameList).map(n -> "N:" + n).orElse(null),
                "UID:" + uid,
                "EMAIL:" + email.value(),
                "END:VCARD"
            )
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining("\n"));
    }

    private String formatNameList(List<String> names) {
        String lastName = !names.isEmpty() ? names.get(0) : "";
        String firstName = names.size() > 1 ? names.get(1) : "";
        String middleName = names.size() > 2 ? names.get(2) : "";
        return "%s;%s;%s;;".formatted(lastName, firstName, middleName);
    }

}
