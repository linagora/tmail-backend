/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 *******************************************************************/

package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

public interface LlmMailPrioritizationClassifierContract {
    Username BOB = Username.of("bob@example.com");
    Username ALICE = Username.of("alice@example.com");

    LlmMailPrioritizationClassifier testee();

    default void needActionsLlmHook() {

    }

    default void noNeedActionsLlmHook() {

    }

    @Test
    default void urgentMailShouldBeQualifiedAsNeedActions() throws Exception {
        needActionsLlmHook();

        testee().init(FakeMailetConfig
            .builder()
            .build());

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender(BOB.asString())
            .addRecipient(ALICE.asString())
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("URGENT – Production API Failure")
                .addFrom(BOB.asString())
                .addToRecipient(ALICE.asString())
                .setText("""
                    Hi team,
                    Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                    Please acknowledge as soon as possible.
                    Thanks,
                    Robert
                    """)
                .build())
            .build();

        testee().service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .containsOnly(new Attribute(keywordAttribute, AttributeValue.of(ImmutableSet.of(
                    AttributeValue.of("needs-action")))));
    }

    @Test
    default void urgentMailWithFoldingFromHeaderShouldBeQualifiedAsNeedActions() throws Exception {
        needActionsLlmHook();

        testee().init(FakeMailetConfig
            .builder()
            .build());

        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setSubject("URGENT – Production API Failure")
            .setText("""
                Hi team,
                Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                Please acknowledge as soon as possible.
                Thanks,
                Robert
                """)
            .addFrom("=?UTF-8?B?Qm9iIEV4YW1wbGUgYSB2ZXJ5IGxvbmcgbmFtZSB0aGF0IHdpbGwgYmUgbGluZSBmb2xkZWQgdG8gdGVzdA==?=\r\n <bob@example.com>") // Bob Example a very long name that will be line folded to test <bob@example.com>
            .addToRecipient(ALICE.asString())
            .build();

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender(BOB.asString())
            .addRecipient(ALICE.asString())
            .mimeMessage(mimeMessage)
            .build();

        testee().service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .containsOnly(new Attribute(keywordAttribute, AttributeValue.of(ImmutableSet.of(
                AttributeValue.of("needs-action")))));
    }

    @Test
    default void urgentMailWithHtmlBodyShouldBeQualifiedAsNeedActions() throws Exception {
        needActionsLlmHook();

        testee().init(FakeMailetConfig
            .builder()
            .build());

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender("billing@twake.app")
            .addRecipient(ALICE.asString())
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("Payment Reminder – Invoice #7842 Due in 24 Hours")
                .addFrom("billing@example.com")
                .addToRecipient(ALICE.asString())
                .setContent(MimeMessageBuilder.multipartBuilder()
                    .subType("alternative")
                    .addBody(MimeMessageBuilder.bodyPartBuilder()
                        .data("""
                            <html>
                              <body>
                                <p>Hi Alice,</p>
                                <p><strong>Action required within 24 hours.</strong></p>
                                <p>
                                  Our records show that <strong>Invoice #7842</strong> (amount:
                                  <strong>$129.00</strong>) for your <em>Twake Mail Premium</em> subscription
                                  is still unpaid.
                                </p>
                                <p>
                                  If payment is not received by <strong>tomorrow</strong>, your account may be
                                  <span style="color: #c0392b; font-weight: bold;">temporarily suspended</span>
                                  and you could lose access to your email.
                                </p>
                        
                                <p>
                                  Please click the secure payment link below to complete your payment:
                                </p>
                                <p>
                                  <a href="https://billing.twake.app/pay/7842" target="_blank">
                                    Pay Invoice #7842 now
                                  </a>
                                </p>
                                <p>Thank you,<br/>
                                   Billing Team
                                </p>
                              </body>
                            </html>
                            """)
                        .addHeader("Content-Type", "text/html")))
                .build())
            .build();

        testee().service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .containsOnly(new Attribute(keywordAttribute, AttributeValue.of(ImmutableSet.of(
                AttributeValue.of("needs-action")))));
    }

    @Test
    default void newsletterMailShouldNotBeQualifiedAsNeedActions() throws Exception {
        noNeedActionsLlmHook();

        testee().init(FakeMailetConfig
            .builder()
            .build());

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender("letters@dev.com")
            .addRecipient(ALICE.asString())
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("Weekly Newsletter – New Productivity Tips")
                .addFrom("letters@dev.com")
                .addToRecipient(ALICE.asString())
                .setText("""
                    Hi there,
                    Here is your weekly productivity newsletter!
                    In this edition, we share five tips on improving your morning routine, plus some recommended reading.
                    Have a great week!
                    Best,
                    The Productivity Hub Team
                    """)
                .build())
            .build();

        testee().service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .isEmpty();
    }

    @Test
    default void spamMailShouldNotBeQualifiedAsNeedActions() throws Exception {
        noNeedActionsLlmHook();

        testee().init(FakeMailetConfig
            .builder()
            .build());

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender("no-reply@banking.tld")
            .addRecipient(ALICE.asString())
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("URGENT: Your Account Will Be Suspended Today")
                .addFrom("no-reply@banking.tld")
                .addToRecipient(ALICE.asString())
                .setText("""
                    Dear customer,
                    
                    URGENT ACTION REQUIRED!
                    We detected unusual activity on your account and it will be suspended TODAY if you do not verify your information immediately.
                
                    Please click the secure link below to confirm your identity and avoid permanent loss of access:
                    http://secure-verify-account.example-security-check.com
                
                    This is an automated message. Failure to act within 2 hours will result in account termination.
                
                    Best regards,
                    Security Team
                    """)
                .build())
            .build();

        testee().service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .isEmpty();
    }

    @Test
    default void veryLongBodyShouldBeTruncatedAndNotFailTheLlm() throws Exception {
        noNeedActionsLlmHook();

        testee().init(FakeMailetConfig
            .builder()
            .build());

        // Build a very long body (hundreds of thousands of chars)
        String repeatedSegment = "This is a long body segment. ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append(repeatedSegment);
        }
        String longBody = sb.toString();

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender(BOB.asString())
            .addRecipient(ALICE.asString())
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("Large Email Test")
                .addFrom(BOB.asString())
                .addToRecipient(ALICE.asString())
                .setText(longBody)
                .build())
            .build();

        testee().service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .isEmpty();
    }
}
