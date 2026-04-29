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
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
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
import com.linagora.tmail.mailet.CalDavCollect;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.property.Attendee;
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

            public Builder dtStart(String dtStart) {
                this.dtStart = Optional.of(dtStart);
                return this;
            }

            public Builder dtEnd(String dtEnd) {
                this.dtEnd = Optional.of(dtEnd);
                return this;
            }

            public Builder recurrenceId(String recurrenceId) {
                this.recurrenceId = Optional.of(recurrenceId);
                return this;
            }

            public Builder lastModified(String lastModified) {
                this.lastModified = Optional.of(lastModified);
                return this;
            }

            public Builder organizer(EmailTemplateUser organizer) {
                this.organizer = Optional.of(organizer);
                return this;
            }

            public Builder icsAttendee(EmailTemplateUser icsAttendee) {
                this.icsAttendee = Optional.of(icsAttendee);
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
                    location.orElse("office"),
                    dtStart.orElse("20170111T090000Z"),
                    dtEnd.orElse("20170111T100000Z"),
                    recurrenceId.orElse("20170112T090000Z"),
                    lastModified.orElse("20170106T115036Z"),
                    organizer.orElse(sender),
                    icsAttendee.orElse(sender));
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
    private OpenPaasUser notInvited;

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
                            .mailet(CalDavCollect.class)
                            .addProperty("alignmentMode", alignmentMode))
                        .addMailetsFrom(CommonProcessors.deliverOnlyTransport())))
            .build(dir);

        jamesServer.start();

        davClient = new DavClient(openPaasExtension.dockerOpenPaasSetup().davConfiguration());

        sender = createUser();
        receiver = createUser();
        notInvited = createUser();

        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(OpenPaaSProvisioningService.DOMAIN)
            .addUser(sender.email().asString(), PASSWORD)
            .addUser(receiver.email().asString(), PASSWORD)
            .addUser(notInvited.email().asString(), PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void mailetShouldCallDavSeverToCreateNewCalendarObject(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, mimeMessageId));

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(mimeMessageId)).block();
        assertThat(result).isNotNull();
    }

    @Test
    void mailetShouldNotCallDavServerToCreateNewCalendarObjectIfRecipientNotInvited(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, mimeMessageId));

        sendMessage(sender, notInvited, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(notInvited.email()).getCalendarObject(new DavUser(notInvited.id(), notInvited.email()), new DavUid(mimeMessageId)).block();
        assertThat(result).isNull();
    }

    @Test
    void mailetShouldCallDavServerToCreateNewCalendarObjectIfRecipientIsOrganizer(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, mimeMessageId));

        sendMessage(receiver, sender, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(sender.email()).getCalendarObject(new DavUser(sender.id(), sender.email()), new DavUid(mimeMessageId)).block();
        assertThat(result).isNotNull();
    }

    @Test
    void mailetShouldOnlyCallDavServerToCreateNewCalendarObjectForInvitedRecipientsWhenMailContainsBothInvitedRecipientsAndUninvitedRecipients(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, mimeMessageId));

        sendMessage(sender, ImmutableList.of(receiver, notInvited), mail, mimeMessageId);

        DavCalendarObject result1 = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(mimeMessageId)).block();

        DavCalendarObject result2 = davClient.caldav(notInvited.email()).getCalendarObject(new DavUser(notInvited.id(), notInvited.email()), new DavUid(mimeMessageId)).block();

        assertSoftly(softly -> {
            softly.assertThat(result1).isNotNull();
            softly.assertThat(result2).isNull();
        });

    }

    @Test
    void mailetShouldCallDavSeverToDeleteCalendarObjectWhenIcsMethodIsCancel(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));

        sendMessage(sender, receiver, mail, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .method("CANCEL")
                .build());

        sendMessage(sender, receiver, mail2, mimeMessageId2);

        DavCalendarObject result = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.STATUS).get().getValue()).isEqualTo("CANCELLED");
    }

    @Test
    void mailetShouldCallDavSeverToUpdateCalendarObject(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache", generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));

        sendMessage(sender, receiver, mail, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .sequence("1")
                .location("office2")
                .build());

        sendMessage(sender, receiver, mail2, mimeMessageId2);

        DavCalendarObject result = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office2");
    }

    @Test
    void mailetShouldCallDavSeverToCreateNewVEventInRecurringCalendar(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .lastModified("20170106T115036Z")
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithRecurrenceId.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .lastModified("20170106T125036Z")
                .location("office2")
                .build());

        sendMessage(sender, receiver, mail2, mimeMessageId2);

        DavCalendarObject result = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(getVEventContainingRecurrenceId(result.calendarData()).get().getProperty(Property.RECURRENCE_ID).get().getValue())
            .isEqualTo("20170112T090000Z");
    }

    @Test
    void mailetShouldCallDavSeverToUpdateVEventInRecurringCalendar(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .lastModified("20170106T115036Z")
                .build());
        sendMessage(sender, receiver, mail, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithRecurrenceId.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .lastModified("20170106T125036Z")
                .location("office2")
                .build());
        sendMessage(sender, receiver, mail2, mimeMessageId2);

        String mimeMessageId3 = UUID.randomUUID().toString();
        String mail3 = generateMail("template/emailWithRecurrenceId.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId3)
                .calendarUid(calendarUid)
                .lastModified("20170106T135036Z")
                .location("office3")
                .build());
        sendMessage(sender, receiver, mail3, mimeMessageId3);

        DavCalendarObject result = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(getVEventContainingRecurrenceId(result.calendarData()).get().getProperty(Property.LOCATION).get().getValue())
            .isEqualTo("office3");
    }

    @Test
    void mailetShouldCallDavSeverToDeleteEventInRecurringCalendar(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .lastModified("20170106T115036Z")
                .build());
        sendMessage(sender, receiver, mail, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithRecurrenceId.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .method("CANCEL")
                .lastModified("20170106T125036Z")
                .build());

        sendMessage(sender, receiver, mail2, mimeMessageId2);

        DavCalendarObject result = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.EXDATE).get().getValue())
            .isEqualTo("20170112T090000Z");
    }

    @Test
    void mailetShouldCallDavSeverToDeleteUpdatedEventInRecurringCalendar(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .lastModified("20170106T115036Z")
                .build());
        sendMessage(sender, receiver, mail, mimeMessageId);

        String mimeMessageId2 = UUID.randomUUID().toString();
        String mail2 = generateMail("template/emailWithRecurrenceId.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId2)
                .calendarUid(calendarUid)
                .lastModified("20170106T125036Z")
                .location("office2")
                .build());

        sendMessage(sender, receiver, mail2, mimeMessageId2);

        String mimeMessageId3 = UUID.randomUUID().toString();
        String mail3 = generateMail("template/emailWithRecurrenceId.eml.mustache",
            EmailTemplateData.builder().sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId3)
                .calendarUid(calendarUid)
                .method("CANCEL")
                .lastModified("20170106T135036Z")
                .build());
        sendMessage(sender, receiver, mail3, mimeMessageId3);

        DavCalendarObject result = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result.calendarData().getComponent(Component.VEVENT).get().getProperty(Property.EXDATE).get().getValue())
            .isEqualTo("20170112T090000Z");
        assertThat(getVEventContainingRecurrenceId(result.calendarData()))
            .isEmpty();
    }

    @Test
    void mailetShouldCallDavServerWhenReplyWithoutOrganizerField(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();

        // First create the event in the organizer's (sender's) calendar via a REQUEST
        String requestMail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));
        sendMessage(receiver, sender, requestMail, mimeMessageId);

        // Then receiver sends a REPLY without the ORGANIZER field (non-compliant external client)
        String replyMimeMessageId = UUID.randomUUID().toString();
        String replyMail = generateMail("template/emailReplyWithoutOrganizer.eml.mustache",
            EmailTemplateData.builder()
                .sender(getReceiver())
                .receiver(getSender())
                .mimeMessageId(replyMimeMessageId)
                .calendarUid(calendarUid)
                .build());
        sendMessage(receiver, sender, replyMail, replyMimeMessageId);

        // The organizer's calendar should be updated with the attendee's PARTSTAT=ACCEPTED
        DavCalendarObject result = davClient.caldav(sender.email()).getCalendarObject(new DavUser(sender.id(), sender.email()), new DavUid(calendarUid)).block();
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

    @Test
    void mailetShouldNotCallDavServerForAttendeeWhenTheyReceiveTheirOwnReply(@TempDir File temporaryFolder) throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();

        // receiver sends a REPLY (without ORGANIZER) but also receives it (e.g. CC'd on their own reply)
        String replyMimeMessageId = UUID.randomUUID().toString();
        String replyMail = generateMail("template/emailReplyWithoutOrganizer.eml.mustache",
            EmailTemplateData.builder()
                .sender(getReceiver())
                .receiver(getSender())
                .mimeMessageId(replyMimeMessageId)
                .calendarUid(calendarUid)
                .build());
        sendMessage(receiver, receiver, replyMail, replyMimeMessageId);

        // receiver is explicitly the ATTENDEE in the REPLY iCal, so no ITIP should be sent for them
        DavCalendarObject result = davClient.caldav(receiver.email()).getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result).isNull();
    }

    private EmailTemplateUser getReceiver() {
        return new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email().asString());
    }

    private EmailTemplateUser getSender() {
        return new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email().asString());
    }

    private void sendMessage(OpenPaasUser sender, OpenPaasUser receiver, String mail, String mimeMessageId) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        sendMessage(sender, ImmutableList.of(receiver), mail, mimeMessageId);
    }

    private void sendMessage(OpenPaasUser sender, List<OpenPaasUser> receivers, String mail, String mimeMessageId) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        List<String> receiverEmails = receivers.stream()
                .map(u -> u.email().asString())
                .toList();

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(sender.email().asString(), PASSWORD)
            .sendMessageWithHeaders(sender.email().asString(), receiverEmails, mail);

        receivers.forEach(receiver -> awaitMessage(receiver, mimeMessageId));
    }

    private void awaitMessage(OpenPaasUser receiver, String mimeMessageId) {
        CALMLY_AWAIT.atMost(5, TimeUnit.SECONDS)
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
                receiver.email().asString(), 1).stream().findFirst();
    }

    private String generateMail(String templateFile, EmailTemplateData emailTemplateData) throws IOException {
        Mustache emailTemplate = MUSTACHE_FACTORY.compile(templateFile);
        StringWriter stringWriter = new StringWriter();
        emailTemplate.execute(stringWriter, emailTemplateData).flush();
        return stringWriter.toString();
    }

    private EmailTemplateData generateEmailTemplateData(OpenPaasUser sender, OpenPaasUser receiver, String mimeMessageId, String calendarUid) {
        return EmailTemplateData.builder().sender(new EmailTemplateUser(sender.firstname() + " " + sender.lastname(), sender.email().asString()))
            .receiver(new EmailTemplateUser(receiver.firstname() + " " + receiver.lastname(), receiver.email().asString()))
            .mimeMessageId(mimeMessageId)
            .calendarUid(calendarUid)
            .build();
    }

    // ---- alignmentMode tests ----

    @Test
    void alignmentModeStrictShouldSkipRequestWhenOrganizerDoesNotMatchSender() throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        // From=sender.email but ORGANIZER=some other address on same domain
        EmailTemplateUser fakeOrganizer = new EmailTemplateUser("Fake Org", "fake-org@" + OpenPaaSProvisioningService.DOMAIN);
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .organizer(fakeOrganizer)
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result).isNull();
    }

    @Test
    void alignmentModeStrictShouldSkipCancelWhenOrganizerDoesNotMatchSender() throws Exception {
        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        // First REQUEST with correct organizer — event is created
        String requestMail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            generateEmailTemplateData(sender, receiver, mimeMessageId, calendarUid));
        sendMessage(sender, receiver, requestMail, mimeMessageId);

        // Then CANCEL with mismatched organizer — event must NOT be cancelled
        String cancelMsgId = UUID.randomUUID().toString();
        EmailTemplateUser fakeOrganizer = new EmailTemplateUser("Fake Org", "fake-org@" + OpenPaaSProvisioningService.DOMAIN);
        String cancelMail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(cancelMsgId)
                .calendarUid(calendarUid)
                .method("CANCEL")
                .organizer(fakeOrganizer)
                .build());
        sendMessage(sender, receiver, cancelMail, cancelMsgId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        // Event still exists and is NOT cancelled
        assertThat(result.calendarData().getComponent(Component.VEVENT).get()
            .getProperty(Property.STATUS).get().getValue())
            .isEqualTo("CONFIRMED");
    }

    @Test
    void alignmentModeStrictShouldSkipReplyWhenSenderNotInAttendees() throws Exception {
        String calendarUid = UUID.randomUUID().toString();
        // Create the event in organizer's calendar first
        String requestMsgId = UUID.randomUUID().toString();
        String requestMail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            generateEmailTemplateData(sender, receiver, requestMsgId, calendarUid));
        sendMessage(receiver, sender, requestMail, requestMsgId);

        // REPLY where From=sender but ICS ATTENDEE is a different address
        String replyMsgId = UUID.randomUUID().toString();
        EmailTemplateUser differentAttendee = new EmailTemplateUser("Other", "other@" + OpenPaaSProvisioningService.DOMAIN);
        String replyMail = generateMail("template/emailReplyWithoutOrganizer.eml.mustache",
            EmailTemplateData.builder()
                .sender(getReceiver())
                .receiver(getSender())
                .mimeMessageId(replyMsgId)
                .calendarUid(calendarUid)
                .icsAttendee(differentAttendee)
                .build());
        sendMessage(receiver, sender, replyMail, replyMsgId);

        // Organizer's calendar should NOT be updated (alignment failed)
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
    void alignmentModeSameDomainShouldAllowRequestWhenOrganizerHasSameDomain(@TempDir File altDir) throws Exception {
        jamesServer.shutdown();
        startServer(altDir, "sameDomain");

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        // From=sender@open-paas.org, ORGANIZER=other@open-paas.org — same domain, different user
        EmailTemplateUser sameDomainOrganizer = new EmailTemplateUser("Same Domain Org", "same-domain-org@" + OpenPaaSProvisioningService.DOMAIN);
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .organizer(sameDomainOrganizer)
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result).isNotNull();
    }

    @Test
    void alignmentModeSameDomainShouldSkipRequestWhenOrganizerHasDifferentDomain(@TempDir File altDir) throws Exception {
        jamesServer.shutdown();
        startServer(altDir, "sameDomain");

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        // From=sender@open-paas.org, ORGANIZER=someone@external.tld — different domain
        EmailTemplateUser externalOrganizer = new EmailTemplateUser("External Org", "organizer@external.tld");
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .organizer(externalOrganizer)
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result).isNull();
    }

    @Test
    void alignmentModeNoneShouldAllowRequestWithAnyOrganizer(@TempDir File altDir) throws Exception {
        jamesServer.shutdown();
        startServer(altDir, "none");

        String mimeMessageId = UUID.randomUUID().toString();
        String calendarUid = UUID.randomUUID().toString();
        // From=sender@open-paas.org, ORGANIZER=anyone@external.tld — no constraint in none mode
        EmailTemplateUser externalOrganizer = new EmailTemplateUser("External Org", "organizer@external.tld");
        String mail = generateMail("template/emailWithAliceInviteBob.eml.mustache",
            EmailTemplateData.builder()
                .sender(getSender())
                .receiver(getReceiver())
                .mimeMessageId(mimeMessageId)
                .calendarUid(calendarUid)
                .organizer(externalOrganizer)
                .build());

        sendMessage(sender, receiver, mail, mimeMessageId);

        DavCalendarObject result = davClient.caldav(receiver.email())
            .getCalendarObject(new DavUser(receiver.id(), receiver.email()), new DavUid(calendarUid)).block();
        assertThat(result).isNotNull();
    }

    // ---- end alignmentMode tests ----

    private Optional<CalendarComponent> getVEventContainingRecurrenceId(Calendar calendar) {
        return calendar.getComponents().stream()
            .filter(component -> Component.VEVENT.equals(component.getName()))
            .filter(component -> component.getProperty(Property.RECURRENCE_ID).isPresent())
            .findAny();
    }

    private OpenPaasUser createUser() {
        return DockerOpenPaasSetup.SINGLETON
            .getOpenPaaSProvisioningService()
            .createUser()
            .block();
    }
}
