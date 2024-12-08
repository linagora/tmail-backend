package com.linagora.tmail.api;

import java.time.Instant;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MailReportGenerator {
    Mono<Void> append(MailReportEntry entry);

    Flux<MailReportEntry> generateReport(Instant start, Instant end);
}
