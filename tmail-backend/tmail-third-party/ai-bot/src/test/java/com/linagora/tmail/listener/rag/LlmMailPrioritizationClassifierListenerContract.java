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
 ********************************************************************/

package com.linagora.tmail.listener.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.nio.charset.StandardCharsets;

import jakarta.mail.Flags;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public interface LlmMailPrioritizationClassifierListenerContract {
    ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    Username BOB = Username.of("bob@example.com");
    Username ALICE = Username.of("alice@example.com");

    MessageManager aliceInbox();
    MessageManager aliceCustomMailbox();
    MailboxSession aliceSession();
    HierarchicalConfiguration<ImmutableNode> listenerConfig();
    void resetListenerWithConfig(HierarchicalConfiguration<ImmutableNode> overrideConfig);
    void registerListenerToEventBus();

    default void needActionsLlmHook() {

    }

    default void noNeedActionsLlmHook() {

    }

    Mono<Flags> readFlags(MessageId messageId, MailboxSession userSession) throws Exception;

    @Test
    default void urgentEmailShouldBeTaggedNeedsAction() throws Exception {
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .contains("needs-action");
            });
    }

    @Test
    default void urgentEmailWithFromHavingDisplayNameShouldBeTaggedNeedsAction() throws Exception {
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom("Bob <" + BOB.asString() + ">")
                        .setTo("Alice <" + ALICE.asString() + ">")
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .contains("needs-action");
            });
    }

    @Test
    default void urgentEmailWithHtmlBodyShouldBeTaggedNeedsAction() throws Exception {
        registerListenerToEventBus();
        needActionsLlmHook();

        BasicBodyFactory bodyFactory = new BasicBodyFactory();
        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("Payment Reminder – Invoice #7842 Due in 24 Hours")
                        .setMessageId("Message-ID")
                        .setFrom("billing@twake.app")
                        .setTo(ALICE.asString())
                        .setBody(MultipartBuilder.create("alternative")
                            .addBodyPart(BodyPartBuilder.create().setBody(bodyFactory
                                    .textBody("""
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
                                        """, "UTF-8"))
                                .setContentType("text/html"))
                            .build())),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .contains("needs-action");
            });
    }

    @Test
    default void newsletterMailShouldNotBeTaggedNeedsAction() throws Exception {
        registerListenerToEventBus();
        noNeedActionsLlmHook();

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("Weekly Newsletter – New Productivity Tips")
                        .setMessageId("Message-ID")
                        .setFrom("letters@dev.com")
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi there,
                            Here is your weekly productivity newsletter!
                            In this edition, we share five tips on improving your morning routine, plus some recommended reading.
                            Have a great week!
                            Best,
                            The Productivity Hub Team
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .doesNotContain("needs-action");
            });
    }

    @Test
    default void spamMailShouldNotBeTaggedNeedsAction() throws Exception {
        registerListenerToEventBus();
        noNeedActionsLlmHook();

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT: Your Account Will Be Suspended Today")
                        .setMessageId("Message-ID")
                        .setFrom("no-reply@banking.tld")
                        .setTo(ALICE.asString())
                        .setBody("""
                            Dear customer,
                            
                            URGENT ACTION REQUIRED!
                            We detected unusual activity on your account and it will be suspended TODAY if you do not verify your information immediately.
                        
                            Please click the secure link below to confirm your identity and avoid permanent loss of access:
                            http://secure-verify-account.example-security-check.com
                        
                            This is an automated message. Failure to act within 2 hours will result in account termination.
                        
                            Best regards,
                            Security Team
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .doesNotContain("needs-action");
            });
    }

    @Test
    default void veryLongBodyShouldBeTruncatedAndNotFailTheLlm() throws Exception {
        registerListenerToEventBus();
        noNeedActionsLlmHook();

        // Build a very long body (hundreds of thousands of chars)
        String longBody = "This is a long body segment. ".repeat(10000);

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("Large Email Test")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody(longBody, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .doesNotContain("needs-action");
            });
    }

    @Test
    default void shouldNotClassifyTheMailIfNotIsDelivery() throws Exception {
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(false)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .doesNotContain("needs-action");
            });
    }

    @Test
    default void shouldNotClassifyTheMailIfNotAppendToInbox() throws Exception {
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId messageId = aliceCustomMailbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .doesNotContain("needs-action");
            });
    }

    @Test
    default void limitMaxReportBodyLengthShouldWork() throws Exception {
        noNeedActionsLlmHook();

        // Basically empty body that LLM should have not enough data to qualify the mail as needs-action
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.setProperty("listener.configuration.maxBodyLength", 1);
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("Production API")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .doesNotContain("needs-action");
            });
    }

}
