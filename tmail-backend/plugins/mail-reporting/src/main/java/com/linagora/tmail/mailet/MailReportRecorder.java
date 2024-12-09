package com.linagora.tmail.mailet;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.util.ReactorUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.linagora.tmail.api.MailReportEntry;
import com.linagora.tmail.api.MailReportGenerator;

import reactor.core.publisher.Flux;

public class MailReportRecorder extends GenericMailet {
    private final MailReportGenerator mailReportGenerator;
    private final Clock clock;
    private MailReportEntry.Kind kind;

    @Inject
    public MailReportRecorder(MailReportGenerator mailReportGenerator, Clock clock) {
        this.mailReportGenerator = mailReportGenerator;
        this.clock = clock;
    }

    @Override
    public void init() throws MessagingException {
        kind = Optional.ofNullable(getInitParameter("kind"))
            .flatMap(MailReportEntry.Kind::parse)
            .orElseThrow(() -> new MessagingException("Unparsable or missing kind property"));
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Instant instant = clock.instant();
        String subject = Optional.ofNullable(mail.getMessage().getSubject()).orElse("<no subject>");

        Flux.fromIterable(mail.getRecipients())
            .map(recipient -> new MailReportEntry(kind,
                subject,
                mail.getMaybeSender(),
                recipient,
                instant))
            .flatMap(mailReportGenerator::append, ReactorUtils.DEFAULT_CONCURRENCY)
            .then()
            .block();
    }
}
