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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;
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
import org.apache.james.transport.mailets.ICALToJsonAttribute;
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
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.linagora.tmail.dav.DavCalendarObject;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUid;
import com.linagora.tmail.dav.DavUser;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngineModule;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.mailet.RestrictiveCalDavCollect;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.property.Attendee;
import reactor.core.publisher.Mono;

public class RestrictiveCalDavCollectIntegrationTest {

    private record EmailTemplateUser(String name, String email) {}

    private record EmailTemplateData(EmailTemplateUser sender,
                                     EmailTemplateUser receiver,
                                     String mimeMessageId,
                                     String calendarUid,
                                     String method,
                                     String sequence,
                                     String dtStamp,
                                     String location,
                                     String dtStart,
                                     String dtEnd,
                                     String recurrenceId,
                                     String lastModified,
                                     EmailTemplateUser organizer,
                                     EmailTemplateUser icsAttendee) {
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
            private Optional<String> dtStart = Optional.empty();
            private Optional<String> dtEnd = Optional.empty();
            private Optional<String> recurrenceId = Optional.empty();
            private Optional<String> lastModified = Optional.empty();
            private Optional<EmailTemplateUser> organizer = Optional.empty();
            private Optional<EmailTemplateUser> icsAttendee = Optional.empty();

            public Builder sender(EmailTemplateUser sender) { this.sender = sender; return this; }
            public Builder receiver(EmailTemplateUser receiver) { this.receiver = receiver; return this; }
            public Builder mimeMessageId(String mimeMessageId) { this.mimeMessageId = mimeMessageId; return this; }
            public Builder calendarUid(String calendarUid) { this.calendarUid = calendarUid; return this; }
            public Builder method(String method) { this.method = Optional.of(method); return this; }
            public Builder sequence(String sequence) { this.sequence = Optional.of(sequence); return this; }
            public Builder location(String location) { this.location = Optional.of(location); return this; }
            public Builder organizer(EmailTemplateUser organizer) { this.organizer = Optional.of(organizer); return this; }
            public Builder icsAttendee(EmailTemplateUser icsAttendee) { this.icsAttendee = Optional.of(icsAttendee); return this; }

            public EmailTemplateData build() {
                Preconditions.checkNotNull(sender, "sender is required");
                Preconditions.checkNotNull(receiver, "receiver is required");
                Preconditions.checkNotNull(mimeMessageId, "mimeMessageId is required");
                Preconditions.checkNotNull(calendarUid, "calendarUid is required");
                return new EmailTemplateData(sender, receiver, mimeMessageId, calendarUid,
                    method.orElse("REQUEST"), sequence.orElse("0"),
                    dtStamp.orElse("20170106T115036Z"), location.orElse("office"),
                    dtStart.orElse("20170111T090000Z"), dtEnd.orElse("20170111T100000Z"),
                    recurrenceId.orElse("20170112T090000Z"), lastModified.orElse("20170106T115036Z"),
                    organizer.orElse(sender), icsAttendee.orElse(sender));
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
    private OpenPaasUser sender;
    private OpenPaasUser receiver;

    @BeforeEach
    void setUpJamesServer(@TempDir File temporaryFolder) throws Exception {
        startServer(temporaryFolder, "strict");
    }

    private void startServer(File dir, String alignmentMode) throws Exception {
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
                            .addProperty(ICalendarParser.SOURCE_ATTRIBUTE_PARAMETER_NAME, "rawIcalendar")
                            .addProperty(ICalendarParser.DESTINATION_ATTRIBUTE_PARAMETER_NAME, "icalendar"))
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(ICALToJsonAttribute.class)
                            .addProperty(ICALToJsonAttribute.RAW_SOURCE_ATTRIBUTE_NAME, "rawIcalendar"))
                        .addMailet(MailetConfiguration.builder()
                            .matcher(All.class)
                            .mailet(RestrictiveCalDavCollect.class)
                            .addProperty("alignmentMode", alignmentMode))
                        .addMailetsFrom(CommonProcessors.deliverOnlyTransport())))
            .build(dir);

        jamesServer.start();

        davClient = new DavClient(openPaasExtension.dockerOpenPaasSetup().davConfiguration());
        sender = createUser();
        receiver = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email().asString(), PASSWORD)
            .addUser(receiver.email().asString(), PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void mailetShouldNotImportNewInviteIntoCalDAV() throws Exception {
        String calendarUid = UUID.randomUUID().toString();
        String mimeMessageId = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            buildTemplateData(sender, receiver, mimeMessageId, calendarUid));

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result).isNull();
    }

    @Test
    void mailetShouldUpdateCalDAVWhenEventAlreadyExists() throws Exception {
        String calendarUid = UUID.randomUUID().toString();

        // Pre-push the event directly into the receiver's CalDAV calendar
        pushCalendarToDav(receiver, calendarUid, "office");

        // Send an updated invite (new location)
        String mimeMessageId = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(toTemplateUser(sender))
                .receiver(toTemplateUser(receiver))
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .sequence("1")
                .location("office2")
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get()
            .getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office2");
    }

    @Test
    void mailetShouldHandleReplyNormally() throws Exception {
        String calendarUid = UUID.randomUUID().toString();

        // Pre-push the event into the organizer's (sender's) calendar
        pushCalendarToDav(sender, calendarUid, "office");

        // Receiver sends a REPLY to the organizer
        String replyMimeMessageId = UUID.randomUUID().toString();
        String replyMail = generateMail("template/emailReplyWithoutOrganizer.eml.mustache",
            EmailTemplateData.builder()
                .sender(toTemplateUser(receiver))
                .receiver(toTemplateUser(sender))
                .mimeMessageId(replyMimeMessageId)
                .calendarUid(calendarUid)
                .build());

        sendMessage(receiver, sender, replyMail, replyMimeMessageId);

        DavCalendarObject result = davClient.caldav(sender.email())
            .getCalendarObject(new DavUser(sender.id(), sender.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get()
            .getProperties(Property.ATTENDEE).stream()
            .map(p -> (Attendee) p)
            .filter(a -> a.getCalAddress().getSchemeSpecificPart().equalsIgnoreCase(receiver.email().asString()))
            .findFirst()
            .flatMap(a -> a.getParameter("PARTSTAT"))
            .map(parameter -> ((Parameter) parameter).getValue())
            .orElse(""))
            .isEqualTo("ACCEPTED");
    }

    // ---- alignmentMode tests ----

    @Test
    void alignmentModeStrictShouldSkipRequestUpdateWhenOrganizerDoesNotMatchSender() throws Exception {
        String calendarUid = UUID.randomUUID().toString();
        pushCalendarToDav(receiver, calendarUid, "office");

        String mimeMessageId = UUID.randomUUID().toString();
        EmailTemplateUser fakeOrganizer = new EmailTemplateUser("Fake Org", "fake-org@" + OpenPaaSProvisioningService.DOMAIN);
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(toTemplateUser(sender))
                .receiver(toTemplateUser(receiver))
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .sequence("1")
                .location("office2")
                .organizer(fakeOrganizer)
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        // Location must stay "office" — update was rejected
        assertThat(result.calendarData().getComponent(Component.VEVENT).get()
            .getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office");
    }

    @Test
    void alignmentModeStrictShouldSkipCancelWhenOrganizerDoesNotMatchSender() throws Exception {
        String calendarUid = UUID.randomUUID().toString();
        pushCalendarToDav(receiver, calendarUid, "office");

        String cancelMsgId = UUID.randomUUID().toString();
        EmailTemplateUser fakeOrganizer = new EmailTemplateUser("Fake Org", "fake-org@" + OpenPaaSProvisioningService.DOMAIN);
        String cancelMail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(toTemplateUser(sender))
                .receiver(toTemplateUser(receiver))
                .mimeMessageId(cancelMsgId)
                .calendarUid(calendarUid)
                .method("CANCEL")
                .organizer(fakeOrganizer)
                .build());

        sendMessage(sender, receiver, cancelMail, cancelMsgId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        // Event must still exist (not null) and must NOT have been cancelled
        assertThat(result).isNotNull();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get()
            .getProperty(Property.STATUS)
            .map(Property::getValue)
            .orElse("NONE"))
            .isNotEqualTo("CANCELLED");
    }

    @Test
    void alignmentModeStrictShouldSkipReplyWhenSenderNotInAttendees() throws Exception {
        String calendarUid = UUID.randomUUID().toString();
        pushCalendarToDav(sender, calendarUid, "office");

        String replyMsgId = UUID.randomUUID().toString();
        EmailTemplateUser differentAttendee = new EmailTemplateUser("Other", "other@" + OpenPaaSProvisioningService.DOMAIN);
        String replyMail = generateMail("template/emailReplyWithoutOrganizer.eml.mustache",
            EmailTemplateData.builder()
                .sender(toTemplateUser(receiver))
                .receiver(toTemplateUser(sender))
                .mimeMessageId(replyMsgId)
                .calendarUid(calendarUid)
                .icsAttendee(differentAttendee)
                .build());

        sendMessage(receiver, sender, replyMail, replyMsgId);

        DavCalendarObject result = davClient.caldav(sender.email())
            .getCalendarObject(new DavUser(sender.id(), sender.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get()
            .getProperties(Property.ATTENDEE).stream()
            .map(p -> (Attendee) p)
            .filter(a -> a.getCalAddress().getSchemeSpecificPart().equalsIgnoreCase(receiver.email().asString()))
            .findFirst()
            .flatMap(a -> a.getParameter("PARTSTAT"))
            .map(parameter -> ((Parameter) parameter).getValue())
            .orElse("NEEDS-ACTION"))
            .isEqualTo("NEEDS-ACTION");
    }

    @Test
    void alignmentModeSameDomainShouldAllowRequestUpdateWhenOrganizerHasSameDomain(@TempDir File altDir) throws Exception {
        jamesServer.shutdown();
        startServer(altDir, "sameDomain");

        String calendarUid = UUID.randomUUID().toString();
        pushCalendarToDav(receiver, calendarUid, "office");

        String mimeMessageId = UUID.randomUUID().toString();
        EmailTemplateUser sameDomainOrganizer = new EmailTemplateUser("Same Domain Org", "same-domain-org@" + OpenPaaSProvisioningService.DOMAIN);
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(toTemplateUser(sender))
                .receiver(toTemplateUser(receiver))
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .sequence("1")
                .location("office2")
                .organizer(sameDomainOrganizer)
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get()
            .getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office2");
    }

    @Test
    void alignmentModeNoneShouldAllowRequestUpdateWithAnyOrganizer(@TempDir File altDir) throws Exception {
        jamesServer.shutdown();
        startServer(altDir, "none");

        String calendarUid = UUID.randomUUID().toString();
        pushCalendarToDav(receiver, calendarUid, "office");

        String mimeMessageId = UUID.randomUUID().toString();
        EmailTemplateUser externalOrganizer = new EmailTemplateUser("External Org", "organizer@external.tld");
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(toTemplateUser(sender))
                .receiver(toTemplateUser(receiver))
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .sequence("1")
                .location("office2")
                .organizer(externalOrganizer)
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get()
            .getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office2");
    }

    // ---- end alignmentMode tests ----

    private void pushCalendarToDav(OpenPaasUser user, String calendarUid, String location) {
        String ics = "BEGIN:VCALENDAR\r\n" +
            "VERSION:2.0\r\n" +
            "BEGIN:VEVENT\r\n" +
            "UID:" + calendarUid + "\r\n" +
            "DTSTART:20170111T090000Z\r\n" +
            "DTEND:20170111T100000Z\r\n" +
            "LOCATION:" + location + "\r\n" +
            "ORGANIZER:mailto:" + sender.email().asString() + "\r\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION:mailto:" + receiver.email().asString() + "\r\n" +
            "END:VEVENT\r\n" +
            "END:VCALENDAR\r\n";
        Calendar calendar = CalendarEventParsed.parseICal4jCalendar(
            new ByteArrayInputStream(ics.getBytes(StandardCharsets.UTF_8)));
        URI uri = URI.create("/calendars/" + user.id().value() + "/" + user.id().value() + "/" + calendarUid + ".ics");
        davClient.caldav(user.email()).createCalendar(uri, calendar).block();
    }

    private EmailTemplateData buildTemplateData(OpenPaasUser sender, OpenPaasUser receiver,
                                                String mimeMessageId, String calendarUid) {
        return EmailTemplateData.builder()
            .sender(toTemplateUser(sender))
            .receiver(toTemplateUser(receiver))
            .mimeMessageId(mimeMessageId)
            .calendarUid(calendarUid)
            .build();
    }

    private EmailTemplateUser toTemplateUser(OpenPaasUser user) {
        return new EmailTemplateUser(user.firstname() + " " + user.lastname(), user.email().asString());
    }

    private void sendMessage(OpenPaasUser sender, OpenPaasUser receiver, String mail, String mimeMessageId)
        throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email().asString(), PASSWORD)
            .sendMessageWithHeaders(sender.email().asString(), List.of(receiver.email().asString()), mail);
        awaitMessage(receiver, mimeMessageId);
    }

    private void awaitMessage(OpenPaasUser receiver, String mimeMessageId) {
        CALMLY_AWAIT.atMost(5, TimeUnit.SECONDS)
            .dontCatchUncaughtExceptions()
            .until(() -> searchReceiverInboxForNewMessages(receiver, mimeMessageId).isPresent());
    }

    private Optional<MessageId> searchReceiverInboxForNewMessages(OpenPaasUser receiver, String mimeMessageId) {
        return jamesServer.getProbe(MailboxProbeImpl.class)
            .searchMessage(
                MultimailboxesSearchQuery.from(
                    SearchQuery.of(SearchQuery.mimeMessageID(mimeMessageId))).build(),
                receiver.email().asString(), 1)
            .stream().findFirst();
    }

    private String generateMail(String templateFile, EmailTemplateData data) throws IOException {
        Mustache template = MUSTACHE_FACTORY.compile(templateFile);
        StringWriter writer = new StringWriter();
        template.execute(writer, data).flush();
        return writer.toString();
    }

    private OpenPaasUser createUser() {
        return DockerOpenPaasSetup.SINGLETON
            .getOpenPaaSProvisioningService()
            .createUser()
            .block();
    }
}
