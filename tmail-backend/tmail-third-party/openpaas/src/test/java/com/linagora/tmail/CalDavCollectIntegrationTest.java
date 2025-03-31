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

package com.linagora.tmail;

import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.jmap.routes.BlobResolver;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.ICalendarParser;
import org.apache.james.transport.mailets.MimeDecodingMailet;
import org.apache.james.transport.mailets.StripAttachment;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.base.Preconditions;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.linagora.tmail.dav.DavCalendarObject;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUser;
import com.linagora.tmail.dav.EventUid;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngineModule;
import com.linagora.tmail.mailet.CalDavCollect;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import reactor.core.publisher.Mono;

public class CalDavCollectIntegrationTest {
    private record EmailTemplateUser(String name, String email){}

    private record EmailTemplateData(EmailTemplateUser sender,
                                     EmailTemplateUser receiver,
                                     String mimeMessageId,
                                     String calendarUid,
                                     String method,
                                     String sequence,
                                     String dtStamp,
                                     String location) {
        public Function<String, String> base64Encode() {
            return (obj) -> Base64.getEncoder().encodeToString(obj.getBytes(StandardCharsets.UTF_8));
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private EmailTemplateUser sender;
            private EmailTemplateUser receiver;
            private String mimeMessageId;
            private String calendarUid;
            private Optional<String> method = Optional.empty();
            private Optional<String> sequence = Optional.empty();
            private Optional<String> dtStamp = Optional.empty();
            private Optional<String> location = Optional.empty();

            public Builder sender(EmailTemplateUser sender) {
                this.sender = sender;
                return this;
            }

            public Builder receiver(EmailTemplateUser receiver) {
                this.receiver = receiver;
                return this;
            }

            public Builder mimeMessageId(String mimeMessageId) {
                this.mimeMessageId = mimeMessageId;
                return this;
            }

            public Builder calendarUid(String calendarUid) {
                this.calendarUid = calendarUid;
                return this;
            }

            public Builder method(String method) {
                this.method = Optional.of(method);
                return this;
            }

            public Builder sequence(String sequence) {
                this.sequence = Optional.of(sequence);
                return this;
            }

            public Builder dtStamp(String dtStamp) {
                this.dtStamp = Optional.of(dtStamp);
                return this;
            }

            public Builder location(String location) {
                this.location = Optional.of(location);
                return this;
            }

            public EmailTemplateData build() {
                Preconditions.checkNotNull(sender, "sender is required");
                Preconditions.checkNotNull(receiver, "receiver is required");
                Preconditions.checkNotNull(mimeMessageId, "mimeMessageId is required");
                Preconditions.checkNotNull(calendarUid, "calendarUid is required");

                return new EmailTemplateData(sender,
                    receiver,
                    mimeMessageId,
                    calendarUid,
                    method.orElse("REQUEST"),
                    sequence.orElse("0"),
                    dtStamp.orElse("20170106T115036Z"),
                    location.orElse("office"));
            }
        }
    }

    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static DockerOpenPaasExtension openPaasExtension = new DockerOpenPaasExtension(DockerOpenPaasSetup.SINGLETON);

    private SMTPMessageSender messageSender = new SMTPMessageSender(OpenPaaSProvisioningService.DOMAIN);
    private TemporaryJamesServer jamesServer;
    private DavClient davClient;

    @BeforeEach
    void setUpJamesServer(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(Modules.combine(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE,
                new OpenPaasModule()))
            .withOverrides(new InMemoryEmailAddressContactSearchEngineModule())
            .withOverrides(new AbstractModule() {
                @Override
                protected void configure() {
                    install(new OpenPaasModule.DavModule());
                }

                @ProvidesIntoSet
                public BlobResolver provideFakeBlobResolver() {
                    return (blobId, mailboxSession) -> Mono.empty();
                }
            })
            .withOverrides(openPaasExtension.openpaasModule())
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .putProcessor(
                    ProcessorConfiguration.transport()
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(StripAttachment.class)
                            .addProperty(StripAttachment.MIMETYPE_PARAMETER_NAME, "text/calendar")
                            .addProperty(StripAttachment.ATTRIBUTE_PARAMETER_NAME, "rawIcalendar"))
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(MimeDecodingMailet.class)
                            .addProperty(MimeDecodingMailet.ATTRIBUTE_PARAMETER_NAME, "rawIcalendar"))
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ICalendarParser.class)
                            .addProperty(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_NAME, "rawIcalendar"))
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(CalDavCollect.class))
                        .addMailetsFrom(CommonProcessors.deliverOnlyTransport())))
            .build(temporaryFolder);

        jamesServer.start();

        davClient = new DavClient(openPaasExtension.dockerOpenPaasSetup().davConfiguration());
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void mailetShouldCallDavSeverToCreateNewCalendarObject(@TempDir File temporaryFolder) throws Exception {
        OpenPaasUser sender = createUser();
        OpenPaasUser receiver = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email(), PASSWORD)
            .addUser(receiver.email(), PASSWORD);

        String mimeMessageId = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, mimeMessageId));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail);

        awaitMessage(receiver, mimeMessageId);

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(receiver.id(), receiver.email()), new EventUid(mimeMessageId)).block();
        assertThat(result).isNotNull();
    }

    @Test
    void mailetShouldCallDavSeverToDeleteCalendarObjectWhenIcsMethodIsCancel(@TempDir File temporaryFolder) throws Exception {
        OpenPaasUser sender = createUser();
        OpenPaasUser receiver = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email(), PASSWORD)
            .addUser(receiver.email(), PASSWORD);

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail);

        awaitMessage(receiver, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email()))
                .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email()))
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .method("CANCEL")
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail2);

        awaitMessage(receiver, mimeMessageId2);

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(receiver.id(), receiver.email()), new EventUid(calendarUid)).block();
        assertThat(result).isNull();
    }

    @Test
    void mailetShouldCallDavSeverToUpdateCalendarObjectWhenNewSequenceIsGreaterThanCurrentSequence(@TempDir File temporaryFolder) throws Exception {
        OpenPaasUser sender = createUser();
        OpenPaasUser receiver = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email(), PASSWORD)
            .addUser(receiver.email(), PASSWORD);

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail);

        awaitMessage(receiver, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email()))
                .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email()))
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .sequence("1")
                .location("office2")
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail2);

        awaitMessage(receiver, mimeMessageId2);

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(receiver.id(), receiver.email()), new EventUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office2");
    }

    @Test
    void mailetShouldCallDavSeverToUpdateCalendarObjectWhenNewSequenceEqualCurrentSequenceAndNewDtStampIsGreaterThanCurrentDtStamp(@TempDir File temporaryFolder) throws Exception {
        OpenPaasUser sender = createUser();
        OpenPaasUser receiver = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email(), PASSWORD)
            .addUser(receiver.email(), PASSWORD);

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail);

        awaitMessage(receiver, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email()))
                .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email()))
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .sequence("0")
                .dtStamp("20180106T115036Z")
                .location("office2")
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail2);

        awaitMessage(receiver, mimeMessageId2);

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(receiver.id(), receiver.email()), new EventUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office2");
    }

    @Test
    void mailetShouldCallDavSeverToUpdateCalendarObjectWhenSequenceDoesNotExistAndNewDtStampIsGreaterThanCurrentDtStamp(@TempDir File temporaryFolder) throws Exception {
        OpenPaasUser sender = createUser();
        OpenPaasUser receiver = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email(), PASSWORD)
            .addUser(receiver.email(), PASSWORD);

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail);

        awaitMessage(receiver, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithNoSequenceIcs.eml.mustache",
            EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email()))
                .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email()))
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .dtStamp("20180106T115036Z")
                .location("office2")
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail2);

        awaitMessage(receiver, mimeMessageId2);

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(receiver.id(), receiver.email()), new EventUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office2");
    }

    @Test
    void mailetShouldNotCallDavSeverToUpdateCalendarObjectWhenNewSequenceIsLessThanCurrentSequence(@TempDir File temporaryFolder) throws Exception {
        OpenPaasUser sender = createUser();
        OpenPaasUser receiver = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email(), PASSWORD)
            .addUser(receiver.email(), PASSWORD);

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email()))
                .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email()))
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .sequence("1")
                .location("office")
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail);

        awaitMessage(receiver, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email()))
                .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email()))
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .sequence("0")
                .location("office2")
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail2);

        awaitMessage(receiver, mimeMessageId2);

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(receiver.id(), receiver.email()), new EventUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office");
    }

    @Test
    void mailetShouldNotCallDavSeverToUpdateCalendarObjectWhenNewSequenceEqualCurrentSequenceAndNewDtStampIsLessThanCurrentDtStamp(@TempDir File temporaryFolder) throws Exception {
        OpenPaasUser sender = createUser();
        OpenPaasUser receiver = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email(), PASSWORD)
            .addUser(receiver.email(), PASSWORD);

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail);

        awaitMessage(receiver, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email()))
                .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email()))
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .dtStamp("20160106T115036Z")
                .location("office2")
                .build());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email(), PASSWORD)
            .sendMessageWithHeaders(sender.email(), receiver.email(), mail2);

        awaitMessage(receiver, mimeMessageId2);

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(receiver.id(), receiver.email()), new EventUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office");
    }

    private void awaitMessage(OpenPaasUser receiver, String mimeMessageId) {
        CALMLY_AWAIT.atMost(5000, TimeUnit.SECONDS)
            .dontCatchUncaughtExceptions()
            .until(() -> {
                Optional<MessageId> maybeMessageId = searchReceiverInboxForNewMessages(receiver, mimeMessageId);
                return maybeMessageId.isPresent();
            });
    }

    private Optional<MessageId> searchReceiverInboxForNewMessages(OpenPaasUser receiver, String mimeMessageId) {
        return jamesServer.getProbe(MailboxProbeImpl.class)
            .searchMessage(
                MultimailboxesSearchQuery.from(
                    SearchQuery.of(SearchQuery.mimeMessageID(mimeMessageId))).build(),
                receiver.email(), 1).stream().findFirst();
    }

    private String generateMail(String templateFile, EmailTemplateData emailTemplateData) throws IOException {
        Mustache emailTemplate = MUSTACHE_FACTORY.compile(templateFile);
        StringWriter stringWriter = new StringWriter();
        emailTemplate.execute(stringWriter, emailTemplateData).flush();
        return stringWriter.toString();
    }

    private EmailTemplateData generateEmailTemplateData(OpenPaasUser sender, OpenPaasUser receiver, String mimeMessageId, String calendarUid) {
        return EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email()))
            .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email()))
            .mimeMessageId(mimeMessageId)
            .calendarUid(calendarUid)
            .build();
    }

    private OpenPaasUser createUser() {
        return DockerOpenPaasSetup.SINGLETON
            .getOpenPaaSProvisioningService()
            .createUser()
            .block();
    }
}
