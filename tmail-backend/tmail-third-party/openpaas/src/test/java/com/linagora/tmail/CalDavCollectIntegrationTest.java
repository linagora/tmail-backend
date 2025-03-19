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

import static com.linagora.tmail.dav.WireMockOpenPaaSServerExtension.ALICE_ID;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;
import java.util.UUID;

import jakarta.inject.Singleton;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.Username;
import org.apache.james.jmap.routes.BlobResolver;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.transport.mailets.ICalendarParser;
import org.apache.james.transport.mailets.MimeDecodingMailet;
import org.apache.james.transport.mailets.StripAttachment;
import org.apache.james.transport.matchers.All;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.dav.DavServerExtension;
import com.linagora.tmail.dav.WireMockOpenPaaSServerExtension;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngineModule;
import com.linagora.tmail.mailet.CalDavCollect;

import reactor.core.publisher.Mono;

public class CalDavCollectIntegrationTest {
    static final Username ALICE = Username.of(DavServerExtension.ALICE);
    static final Username BOB = Username.fromLocalPartWithDomain("bob", DEFAULT_DOMAIN);

    @RegisterExtension
    static WireMockOpenPaaSServerExtension openPaasServerExtension = new WireMockOpenPaaSServerExtension();

    @RegisterExtension
    static DavServerExtension davServerExtension = new DavServerExtension();

    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    private TemporaryJamesServer jamesServer;

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

                @Provides
                @Singleton
                public OpenPaasConfiguration provideOpenPaasConfiguration() {
                    return new OpenPaasConfiguration(
                        openPaasServerExtension.getBaseUrl(),
                        ALICE_ID,
                        WireMockOpenPaaSServerExtension.GOOD_PASSWORD,
                        false,
                        davServerExtension.getDavConfiguration());
                }

                @ProvidesIntoSet
                public BlobResolver provideFakeBlobResolver() {
                    return (blobId, mailboxSession) -> Mono.empty();
                }
            })
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .postmaster(ALICE.asString())
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
        jamesServer.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DEFAULT_DOMAIN)
            .addUser(ALICE.asString(), PASSWORD)
            .addUser(BOB.asString(), PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void mailetShouldCallDavSeverToCreateNewCalendarObject(@TempDir File temporaryFolder) throws Exception {
        String bobOpenPassUid = UUID.randomUUID().toString();
        openPaasServerExtension.setSearchEmailExist(BOB.asString(), bobOpenPassUid);
        davServerExtension.addMockOfFindingUserCalendars(bobOpenPassUid, BOB.asString());
        String uri = "/calendars/" + bobOpenPassUid + "/" + bobOpenPassUid + "/ea127690-0440-404b-af98-9823c855a283.ics";
        davServerExtension.addMockOfCreatingCalendarObject(uri, BOB.asString());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessageWithHeaders(ALICE.asString(), BOB.asString(), ClassLoaderUtils.getSystemResourceAsString("emailWithAliceInviteBobIcsAttachment.eml"));

        testIMAPClient.connect(LOCALHOST_IP,
                jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB.asString(), PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        davServerExtension.assertMockOfCreatingCalendarObjectWasCalled(uri,  BOB.asString(), 1);
    }

    @Test
    void mailetShouldCallDavSeverToDeleteCalendarObjectWhenIcsMethodIsCancel(@TempDir File temporaryFolder) throws Exception {
        String bobOpenPassUid = UUID.randomUUID().toString();
        openPaasServerExtension.setSearchEmailExist(BOB.asString(), bobOpenPassUid);
        String uri = "/calendars/" + bobOpenPassUid + "/" + bobOpenPassUid + "/ea127690-0440-404b-af98-9823c855a283.ics";
        davServerExtension.addMockOfDeletingCalendarObject(uri, BOB.asString());

        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessageWithHeaders(ALICE.asString(), BOB.asString(), ClassLoaderUtils.getSystemResourceAsString("emailWithAliceCancelEventIcsAttachment.eml"));

        testIMAPClient.connect(LOCALHOST_IP,
                jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB.asString(), PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        davServerExtension.assertMockOfDeletingCalendarObjectWasCalled(uri,  BOB.asString(), 1);
    }
}
