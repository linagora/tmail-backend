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
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.server.core.MailImpl;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableSet;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

@Disabled("Requires a valid API key in order to be run")
public class LlmMailPrioritizationClassifierTest {
    private static final Username BOB = Username.of("bob@example.com");
    private static final Username ALICE = Username.of("alice@example.com");

    private MailetContext mailetContext;
    private AIBotConfig aiBotConfig;
    private UsersRepository usersRepository;
    private LlmMailPrioritizationClassifier testee;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(Domain.of("example.com"));
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "password");
        usersRepository.addUser(ALICE, "password");

        aiBotConfig = new AIBotConfig(
            Optional.ofNullable(System.getenv("LLM_API_KEY")).orElse("fake-api-key"),
            new LlmModel("gpt-oss-120b"),
            Optional.of(URI.create("https://ai.linagora.com/api/v1/").toURL()));
        StreamChatLanguageModelFactory streamChatLanguageModelFactory = new StreamChatLanguageModelFactory();
        StreamingChatLanguageModel chatLanguageModel = streamChatLanguageModelFactory.createChatLanguageModel(aiBotConfig);
        testee = new LlmMailPrioritizationClassifier(usersRepository, chatLanguageModel, new JsoupHtmlTextExtractor());
        mailetContext = Mockito.mock(MailetContext.class);
    }

    @Test
    void urgentMailShouldBeQualifiedAsNeedActions() throws Exception {
        testee.init(FakeMailetConfig
            .builder()
            .mailetContext(mailetContext)
            .build());

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender(BOB.asString())
            .addRecipient(ALICE.asString())
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("URGENT – Production API Failure")
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

        testee.service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .containsOnly(new Attribute(keywordAttribute, AttributeValue.of(ImmutableSet.of(
                    AttributeValue.of("needs-action")))));
    }

    @Test
    void newsletterMailShouldNotBeQualifiedAsNeedActions() throws Exception {
        testee.init(FakeMailetConfig
            .builder()
            .mailetContext(mailetContext)
            .build());

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender("letters@dev.com")
            .addRecipient(ALICE.asString())
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("Weekly Newsletter – New Productivity Tips")
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

        testee.service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .isEmpty();
    }

    @Test
    void spamMailShouldNotBeQualifiedAsNeedActions() throws Exception {
        testee.init(FakeMailetConfig
            .builder()
            .mailetContext(mailetContext)
            .build());

        Mail mail = MailImpl.builder()
            .name("mail-id")
            .sender("no-reply@banking.tld")
            .addRecipient(ALICE.asString())
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("URGENT: Your Account Will Be Suspended Today")
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

        testee.service(mail);

        AttributeName keywordAttribute = AttributeName.of("Keywords_" + ALICE.asString());
        assertThat(mail.attributes())
            .filteredOn(attribute -> attribute.getName().equals(keywordAttribute))
            .isEmpty();
    }
}
