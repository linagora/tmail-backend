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
import java.util.Map;

import jakarta.mail.Flags;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.field.address.DefaultAddressParser;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.stream.RawField;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;

import reactor.core.publisher.Mono;

public interface LlmMailClassifierListenerContract {
    ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    Username BOB = Username.of("bob@example.com");
    Username ALICE = Username.of("alice@example.com");
    Username ANDRE = Username.of("andre@example.com");
    String getLabel1Id();
    String getLabel2Id();
    String getLabel3Id();
    String NEES_ACTION_FLAG = LlmMailBackendClassifierListener.SYSTEM_LABELS.get("needs-action").keyword();

    MessageManager aliceInbox();
    MessageManager aliceSpam();
    MessageManager aliceCustomMailbox();
    MailboxSession aliceSession();
    HierarchicalConfiguration<ImmutableNode> listenerConfig();
    void resetListenerWithConfig(HierarchicalConfiguration<ImmutableNode> overrideConfig);
    void registerListenerToEventBus();
    JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils();

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
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
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
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
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
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
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
                    .doesNotContain(NEES_ACTION_FLAG)
                    .contains(getLabel3Id());
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
                    .doesNotContain(NEES_ACTION_FLAG)
                    .contains(getLabel3Id());
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
                    .doesNotContain(NEES_ACTION_FLAG);
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
                    .doesNotContain(NEES_ACTION_FLAG);
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
                    .doesNotContain(NEES_ACTION_FLAG);
            });
    }

    @Test
    default void notTargetedRecipientShouldNotBeTaggedAsNeedsAction() throws Exception {
        registerListenerToEventBus();
        noNeedActionsLlmHook();

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ANDRE.asString())
                        .setCc(DefaultAddressParser.DEFAULT.parseMailbox(String.format("<%s>", ALICE.asString())))
                        .setBody("""
                            Hi Andre,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Bob
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .doesNotContain(NEES_ACTION_FLAG)
                    .contains(getLabel3Id());
            });
    }

    @Test
    default void targetedRecipientShouldBeTaggedAsNeedsAction() throws Exception {
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ANDRE.asString())
                        .setCc(DefaultAddressParser.DEFAULT.parseMailbox(String.format("<%s>", ALICE.asString())))
                        .setBody("""
                            Hi Andre,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            cc @Alice can you please schedule a meeting with Andre to investigate what is going on?
                            Thanks,
                            Bob
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
            });
    }

    @Test
    default void indirectTargetedRecipientShouldBeTaggedAsNeedsAction() throws Exception {
        registerListenerToEventBus();
        needActionsLlmHook();

        // The mail targeted the team (Alice and Andre) in general
        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ANDRE.asString(), ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Bob
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
            });
    }

    @Test
    default void unIntendedForwardedUrgentEmailShouldNotBeTaggedAsNeedsAction() throws Exception {
        registerListenerToEventBus();
        noNeedActionsLlmHook();

        // assume Andre blindly forwarded the urgent email to Alice
        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom(BOB.asString())
                        .setTo(ANDRE.asString())
                        .setBody("""
                            Hi Andre,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Bob
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .doesNotContain(NEES_ACTION_FLAG)
                    .contains(getLabel3Id());
            });
    }

    @Test
    default void intendedForwardedUrgentEmailShouldBeTaggedAsNeedsAction() throws Exception {
        registerListenerToEventBus();
        needActionsLlmHook();

        // Andre forwards the urgent email to Alice and explicitly asks for help
        MessageId messageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("Fw: URGENT – Production API Failure")
                        .setMessageId("Message-ID")
                        .setFrom(ANDRE.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi Alice,
                            Can you please help me handle this urgent incident below? I am not sure what to answer and it needs to be fixed as soon as possible.

                            -------- Forwarded message --------
                            Hi Andre,
                            Our payment gateway API has been failing since 03:12 AM UTC. All customer transactions are currently being rejected. We need an immediate fix or rollback before peak traffic starts in 2 hours.
                            Please acknowledge as soon as possible.
                            Thanks,
                            Bob
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                assertThat(readFlags(messageId, aliceSession()).block()).isNotNull();
                assertThat(readFlags(messageId, aliceSession()).block().getUserFlags())
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
            });
    }

    @Test
    default void urgentEmailShouldNotBeTaggedNeedsActionWhenUserHasNoNeedsActionSettingByDefault() throws Exception {
        jmapSettingsRepositoryUtils().reset(ALICE, Map.of("whatever", "true"));
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
                    .doesNotContain(NEES_ACTION_FLAG);
            });
    }

    @Test
    default void urgentEmailShouldBeTaggedNeedsActionWhenUserEnabledNeedsActionSetting() throws Exception {
        jmapSettingsRepositoryUtils().reset(ALICE, Map.of("ai.label-categorization.enabled", "true"));
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
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
            });
    }

    @Test
    default void urgentEmailShouldNotBeTaggedNeedsActionWhenUserDisabledNeedsActionSetting() throws Exception {
        jmapSettingsRepositoryUtils().reset(ALICE, Map.of("ai.label-categorization.enabled", "false"));
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
                    .doesNotContain(NEES_ACTION_FLAG);
            });
    }

    @Test
    default void filterShouldSupportAutoSubmitted() throws Exception {
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.addProperty("listener.configuration.filter.not.isAutoSubmitted", "");
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId autoRepliedMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-1")
                        .addField(new RawField("Auto-Submitted", "auto-replied"))
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId autoSubmittedNoMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-2")
                        .addField(new RawField("Auto-Submitted", "no"))
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId noHeaderMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-3")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(readFlags(autoRepliedMessageId, aliceSession()).block().getUserFlags())
                    .as("Auto-replied messages should be excluded")
                    .doesNotContain(NEES_ACTION_FLAG);
                softly.assertThat(readFlags(autoSubmittedNoMessageId, aliceSession()).block().getUserFlags())
                    .as("Auto-Submitted: no messages should be allowed")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertThat(readFlags(noHeaderMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages without Auto-Submitted header should be allowed")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertAll();
            });
    }

    @Test
    default void filterShouldSupportHasHeaderCheck() throws Exception {
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.addProperty("listener.configuration.filter.hasHeader[@name]", "X-Priority");
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId withHeaderMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-1")
                        .addField(new RawField("X-Priority", "1"))
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId withoutHeaderMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-2")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(readFlags(withHeaderMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages with the specific header should match")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertThat(readFlags(withoutHeaderMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages without the specific header should not match")
                    .doesNotContain(NEES_ACTION_FLAG);
                softly.assertAll();
            });
    }

    @Test
    default void filterShouldSupportHasHeaderWithValue() throws Exception {
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.addProperty("listener.configuration.filter.hasHeader[@name]", "X-Priority");
        overrideConfig.addProperty("listener.configuration.filter.hasHeader[@value]", "1");
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId correctValueMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-1")
                        .addField(new RawField("X-Priority", "1"))
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId differentValueMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-2")
                        .addField(new RawField("X-Priority", "3"))
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi team,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(readFlags(correctValueMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages with correct header value should match")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertThat(readFlags(differentValueMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages with different header value should not match")
                    .doesNotContain(NEES_ACTION_FLAG);
                softly.assertAll();
            });
    }

    @Test
    default void filterShouldSupportMainRecipient() throws Exception {
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.addProperty("listener.configuration.filter.isMainRecipient", "");
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId mainRecipientMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-1")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi Alice,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId notMainRecipientMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-2")
                        .setFrom(BOB.asString())
                        .setTo(ANDRE.asString())
                        .setCc(DefaultAddressParser.DEFAULT.parseMailbox(String.format("<%s>", ALICE.asString())))
                        .setBody("""
                            Hi Andre,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(readFlags(mainRecipientMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages where user is main recipient should match")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertThat(readFlags(notMainRecipientMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages where user is not main recipient should not match")
                    .doesNotContain(NEES_ACTION_FLAG);
                softly.assertAll();
            });
    }

    @Test
    default void filterShouldSupportInbox() throws Exception {
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.addProperty("listener.configuration.filter.isInINBOX", "");
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId inboxMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-1")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi Alice,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId customMailboxMessageId = aliceCustomMailbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-2")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi Alice,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(readFlags(inboxMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages in inbox should match")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertThat(readFlags(customMailboxMessageId, aliceSession()).block().getUserFlags())
                    .as("Messages not in inbox should not match")
                    .doesNotContain(NEES_ACTION_FLAG);
                softly.assertAll();
            });
    }

    @Test
    default void filterShouldSupportIsSpam() throws Exception {
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.addProperty("listener.configuration.filter.isSpam", "");
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId inboxMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-1")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi Alice,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId spamMessageId = aliceSpam().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-2")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi Alice,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(readFlags(inboxMessageId, aliceSession()).block().getUserFlags())
                    .as("Non-spam messages should not be processed")
                    .doesNotContain(NEES_ACTION_FLAG);
                softly.assertThat(readFlags(spamMessageId, aliceSession()).block().getUserFlags())
                    .as("Spam messages should be processed")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertAll();
            });
    }

    @Test
    default void filterShouldSupportAndOperator() throws Exception {
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.addProperty("listener.configuration.filter.and.isInINBOX", "");
        overrideConfig.addProperty("listener.configuration.filter.and.isMainRecipient", "");
        overrideConfig.addProperty("listener.configuration.filter.and.not.isAutoSubmitted", "");
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId allConditionsMetMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-1")
                        .setFrom(BOB.asString())
                        .setTo(ALICE.asString())
                        .setBody("""
                            Hi Alice,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId oneConditionFailsMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-2")
                        .setFrom(BOB.asString())
                        .setTo(ANDRE.asString())
                        .setCc(DefaultAddressParser.DEFAULT.parseMailbox(String.format("<%s>", ALICE.asString())))
                        .setBody("""
                            Hi Andre,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(readFlags(allConditionsMetMessageId, aliceSession()).block().getUserFlags())
                    .as("AND filter should match when all conditions are met")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertThat(readFlags(oneConditionFailsMessageId, aliceSession()).block().getUserFlags())
                    .as("AND filter should not match when one condition fails")
                    .doesNotContain(NEES_ACTION_FLAG);
                softly.assertAll();
            });
    }

    @Test
    default void filterShouldSupportOrOperator() throws Exception {
        HierarchicalConfiguration<ImmutableNode> overrideConfig = listenerConfig();
        overrideConfig.addProperty("listener.configuration.filter.or.isMainRecipient", "");
        overrideConfig.addProperty("listener.configuration.filter.or.hasHeader[@name]", "X-Priority");
        resetListenerWithConfig(overrideConfig);
        registerListenerToEventBus();
        needActionsLlmHook();

        MessageId oneConditionMetMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-1")
                        .setFrom(BOB.asString())
                        .setTo(ANDRE.asString())
                        .setCc(DefaultAddressParser.DEFAULT.parseMailbox(String.format("<%s>", ALICE.asString())))
                        .addField(new RawField("X-Priority", "1"))
                        .setBody("""
                            Hi Andre,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        MessageId noConditionMetMessageId = aliceInbox().appendMessage(MessageManager.AppendCommand.builder()
                    .isDelivery(true)
                    .build(Message.Builder.of()
                        .setSubject("URGENT – Production API Failure")
                        .setMessageId("Message-ID-2")
                        .setFrom(BOB.asString())
                        .setTo(ANDRE.asString())
                        .setCc(DefaultAddressParser.DEFAULT.parseMailbox(String.format("<%s>", ALICE.asString())))
                        .setBody("""
                            Hi Andre,
                            Our payment gateway API has been failing since 03:12 AM UTC.
                            Thanks,
                            Robert
                            """, StandardCharsets.UTF_8)),
                aliceSession())
            .getId().getMessageId();

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> {
                SoftAssertions softly = new SoftAssertions();
                softly.assertThat(readFlags(oneConditionMetMessageId, aliceSession()).block().getUserFlags())
                    .as("OR filter should match when at least one condition is met")
                    .contains(NEES_ACTION_FLAG, getLabel1Id(), getLabel2Id());
                softly.assertThat(readFlags(noConditionMetMessageId, aliceSession()).block().getUserFlags())
                    .as("OR filter should not match when no conditions are met")
                    .doesNotContain(NEES_ACTION_FLAG);
                softly.assertAll();
            });
    }
}
