/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package com.linagora.tmail.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.junit.jupiter.api.Test;

public interface MailReportGeneratorContract {

    MailReportGenerator testee();

    @Test
    default void generateShouldReturnEmptyByDefault() {
        assertThat(testee().generateReport(Instant.now().minus(Duration.ofDays(1000)), Instant.now())
            .collectList().block())
            .isEmpty();
    }

    @Test
    default void generateShouldReturnStoredValue() throws Exception {
        Instant now = Instant.now();
        MailReportEntry record1 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject1", MaybeSender.getMailSender("bob@domain.tld"),
            new MailAddress("alice@domain.tld"), now.minus(Duration.ofSeconds(1)),
            123L);
        testee().append(record1).block();

        assertThat(testee().generateReport(now.minus(Duration.ofSeconds(10)), now)
            .collectList().block())
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("date")
            .containsExactly(record1);
    }

    @Test
    default void generateShouldFilterStoreValueByDate() throws Exception {
        // Given 3 records
        // record 1: 3 days ago
        Instant now = Instant.now();
        MailReportEntry record1 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject1", MaybeSender.getMailSender("bob1@domain.tld"),
            new MailAddress("alice1@domain.tld"), now.minus(Duration.ofDays(3)),
            123L);
        testee().append(record1).block();

        // record 2: 1 day ago
        MailReportEntry record2 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject2", MaybeSender.getMailSender("bob2@domain.tld"),
            new MailAddress("alice2@doamin.tld"), now.minus(Duration.ofDays(1)),
            124L);
        testee().append(record2).block();

        // record 3: now
        MailReportEntry record3 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject3", MaybeSender.getMailSender("bob3@domain.tld"),
            new MailAddress("alice3@domain.tld"), now,
            125L);
        testee().append(record3).block();

        // When we generate report for the last 2 days
        // Then we should only get record 2 and record 3
        List<String> reportsResultBySubject = testee().generateReport(now.minus(Duration.ofDays(2)), now)
            .map(MailReportEntry::subject)
            .collectList().block();
        assertThat(reportsResultBySubject).hasSize(2);
        assertThat(reportsResultBySubject).doesNotContain("subject1");
        assertThat(reportsResultBySubject).containsExactlyInAnyOrder("subject2", "subject3");
    }

    @Test
    default void generateShouldSupportFilterToSecondUnit() throws Exception {
        Instant now = Instant.now();

        // Given 2 records
        // record 1: 3 seconds ago
        MailReportEntry record1 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject1", MaybeSender.getMailSender("bob1@domain.tld"),
            new MailAddress("alice1@domain.tld"), now.minus(Duration.ofSeconds(3)),
            123L);
        testee().append(record1).block();

        // record 2: 1 second ago
        MailReportEntry record2 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject2", MaybeSender.getMailSender("bob2@domain.tld"),
            new MailAddress("alice2@doamin.tld"), now.minus(Duration.ofSeconds(1)),
            124L);
        testee().append(record2).block();

        // When we generate report for the last 2 seconds
        // Then we should only get record 2
        List<String> reportsResultBySubject = testee().generateReport(now.minus(Duration.ofSeconds(2)), now)
            .map(MailReportEntry::subject)
            .collectList().block();
        assertThat(reportsResultBySubject).hasSize(1);
        assertThat(reportsResultBySubject).containsExactly("subject2");
    }

    @Test
    default void generateShouldSupportFilterToMonthUnit() throws Exception {
        // Given 3 records
        // record 1: 3 months ago
        Instant now = Instant.now();
        MailReportEntry record1 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject1", MaybeSender.getMailSender("bob1@domain.tld"),
            new MailAddress("alice1@domain.tld"), now.minus(Duration.ofDays(90)),
            123L);
        testee().append(record1).block();

        // record 2: 1 month ago
        MailReportEntry record2 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject2", MaybeSender.getMailSender("bob2@domain.tld"),
            new MailAddress("alice2@doamin.tld"), now.minus(Duration.ofDays(30)),
            123L);
        testee().append(record2).block();

        // record 3: now
        MailReportEntry record3 = new MailReportEntry(MailReportEntry.Kind.Sent,
            "subject3", MaybeSender.getMailSender("bob3@domain.tld"),
            new MailAddress("alice3@domain.tld"), now.minus(Duration.ofSeconds(100)),
            123L);
        testee().append(record3).block();

        // When we generate report for the last 2 months
        // Then we should only get record 2 and record 3
        List<String> reportsResultBySubject = testee().generateReport(now.minus(Duration.ofDays(60)), now.plusSeconds(10000))
            .map(MailReportEntry::subject)
            .collectList().block();
        assertThat(reportsResultBySubject).hasSize(2);
        assertThat(reportsResultBySubject).doesNotContain("subject1");
        assertThat(reportsResultBySubject).containsExactlyInAnyOrder("subject2", "subject3");
    }

}
