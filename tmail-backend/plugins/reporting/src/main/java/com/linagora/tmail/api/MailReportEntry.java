package com.linagora.tmail.api;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;

public record MailReportEntry(Kind kind,
                              String subject,
                              MaybeSender sender,
                              MailAddress recipient,
                              Instant date) {
    public enum Kind {
        Sent("SENT"),
        Received("RECEIVED");

        public static Optional<Kind> parse(String value) {
            return Stream.of(Kind.values())
                .filter(kind -> kind.asString().equals(value))
                .findFirst();
        }

        private final String value;

        Kind(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }
    }

}
