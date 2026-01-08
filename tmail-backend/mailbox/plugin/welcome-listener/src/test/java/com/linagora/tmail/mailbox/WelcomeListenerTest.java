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

package com.linagora.tmail.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsPatch$;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class WelcomeListenerTest {
    private static final Username BOB = Username.of("bob");
    private static final JmapSettingsKey LANGUAGE_KEY = JmapSettingsKey.liftOrThrow("language");

    private WelcomeListener testee;
    private InMemoryMailboxManager mailboxManager;
    private JmapSettingsRepository jmapSettingsRepository;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        jmapSettingsRepository = new MemoryJmapSettingsRepository();
    }

    @Test
    void shouldThrowWhenBothI18nAndLegacyEmlLocationNotConfigured() {
        assertThatThrownBy(() -> new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
            config(Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("`eml` must be present when i18n is not configured");
    }

    @Test
    void shouldPreferI18nConfigWhenBothI18nAndLegacyAreConfigured() throws Exception {
        testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
            config(Map.of(
                "supportedLanguages", "en,fr",
                "i18nWelcomeTemplateDirectory", "classpath://eml-template/welcome/",
                "defaultLanguage", "en",
                "eml", "classpath://file.eml")));
        mailboxManager.getEventBus().register(testee);
        Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "fr"))).block();

        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxId mailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(BOB), session)).block();

        Message message = readFirstMessage(mailboxId, session);
        assertThat(message.getSubject()).isEqualTo("Welcome FR");
    }

    @Nested
    class I18n {
        @Test
        void i18nConfigShouldBeEmptyWhenNotConfigured() {
            Optional<WelcomeListener.I18nConfig> i18nConfig = WelcomeListener.I18nConfig.from(config(Map.of(
                "eml", "classpath://file.eml")));

            assertThat(i18nConfig).isEmpty();
        }

        @Test
        void shouldDefaultToEnglishWhenNoDefaultLanguageConfigured() {
            Optional<WelcomeListener.I18nConfig> i18nConfig = WelcomeListener.I18nConfig.from(config(Map.of(
                "supportedLanguages", "en,fr",
                "i18nWelcomeTemplateDirectory", "classpath://eml-template/welcome/")));

            assertThat(i18nConfig).isPresent();
            assertThat(i18nConfig.get().defaultLanguage()).isEqualTo(Locale.ENGLISH);
        }

        @Test
        void shouldFallbackToDefaultClasspathWhenNoTemplateDirectoryConfigured() {
            Optional<WelcomeListener.I18nConfig> i18nConfig = WelcomeListener.I18nConfig.from(config(Map.of(
                "supportedLanguages", "en,fr",
                "defaultLanguage", "en")));

            assertThat(i18nConfig).isPresent();
            assertThat(i18nConfig.get().i18nWelcomeTemplateDirectory()).isEqualTo("classpath://eml-template/welcome/");
        }

        @Test
        void shouldThrowWhenInvalidSupportedLanguageConfigured() {
            assertThatThrownBy(() -> new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
                config(Map.of(
                    "supportedLanguages", "invalidSupportedLanguage"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The provided language 'invalidSupportedLanguage' can not be parsed to a valid Locale.");
        }

        @Test
        void shouldProvisionWelcomeMessageInEnglishWhenUserLanguageSettingIsEnglish() throws Exception {
            testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
                config(Map.of(
                    "supportedLanguages", "en,fr",
                    "i18nWelcomeTemplateDirectory", "classpath://eml-template/welcome/",
                    "defaultLanguage", "en")));
            mailboxManager.getEventBus().register(testee);

            Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "en"))).block();

            MailboxSession session = mailboxManager.createSystemSession(BOB);
            MailboxId mailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(BOB), session)).block();

            Message message = readFirstMessage(mailboxId, session);
            assertThat(message.getSubject()).isEqualTo("Welcome EN");
        }

        @Test
        void shouldProvisionWelcomeMessageInFrenchWhenUserLanguageSettingIsFrench() throws Exception {
            testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
                config(Map.of(
                    "supportedLanguages", "en,fr",
                    "i18nWelcomeTemplateDirectory", "classpath://eml-template/welcome/",
                    "defaultLanguage", "en")));
            mailboxManager.getEventBus().register(testee);

            Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "fr"))).block();

            MailboxSession session = mailboxManager.createSystemSession(BOB);
            MailboxId mailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(BOB), session)).block();

            Message message = readFirstMessage(mailboxId, session);
            assertThat(message.getSubject()).isEqualTo("Welcome FR");
        }

        @Test
        void shouldProvisionWelcomeMessageInDefaultLanguageWhenUserLanguageNotSupported() throws Exception {
            testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
                config(Map.of(
                    "supportedLanguages", "en,fr",
                    "i18nWelcomeTemplateDirectory", "classpath://eml-template/welcome/",
                    "defaultLanguage", "en")));
            mailboxManager.getEventBus().register(testee);

            Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "vi"))).block();

            MailboxSession session = mailboxManager.createSystemSession(BOB);
            MailboxId mailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(BOB), session)).block();

            Message message = readFirstMessage(mailboxId, session);
            assertThat(message.getSubject()).isEqualTo("Welcome EN");
        }

        @Test
        void shouldProvisionWelcomeMessageInDefaultLanguageWhenUserSettingIsUnset() throws Exception {
            testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
                config(Map.of(
                    "supportedLanguages", "en,fr",
                    "i18nWelcomeTemplateDirectory", "classpath://eml-template/welcome/",
                    "defaultLanguage", "en")));
            mailboxManager.getEventBus().register(testee);

            // Bob's language setting is not set

            MailboxSession session = mailboxManager.createSystemSession(BOB);
            MailboxId mailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(BOB), session)).block();

            Message message = readFirstMessage(mailboxId, session);
            assertThat(message.getSubject()).isEqualTo("Welcome EN");
        }
    }

    @Nested
    class LegacySingleTemplate {
        @Test
        void shouldFallbackToLegacyTemplateWhenI18nConfigIsNotProvided() throws Exception {
            testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
                config(Map.of("eml", "classpath://file.eml")));
            mailboxManager.getEventBus().register(testee);

            MailboxSession session = mailboxManager.createSystemSession(BOB);
            MailboxId mailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(BOB), session)).block();

            Message message = readFirstMessage(mailboxId, session);
            assertThat(message.getSubject()).isEqualTo("Welcome!");
        }

        @Test
        void shouldProvisionWelcomeMessageOnInboxCreation() throws Exception {
            testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
                config(Map.of("eml", "classpath://file.eml")));
            mailboxManager.getEventBus().register(testee);
            MailboxSession session = mailboxManager.createSystemSession(BOB);
            MailboxId mailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(BOB), session)).block();

            Long count = Flux.from(mailboxManager.getMailbox(mailboxId, session)
                    .listMessagesMetadata(MessageRange.all(), session))
                .count()
                .block();

            assertThat(count).isEqualTo(1);
        }

        @Test
        void shouldNotProvisionWelcomeMessageOnOtherMailboxCreation() throws Exception {
            testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), jmapSettingsRepository,
                config(Map.of("eml", "classpath://file.eml")));
            mailboxManager.getEventBus().register(testee);
            MailboxSession session = mailboxManager.createSystemSession(BOB);
            MailboxId mailboxId = mailboxManager.createMailbox(MailboxPath.forUser(BOB, "other"), session).get();

            Long count = Flux.from(mailboxManager.getMailbox(mailboxId, session)
                    .listMessagesMetadata(MessageRange.all(), session))
                .count()
                .block();

            assertThat(count).isZero();
        }
    }

    private Message readFirstMessage(MailboxId mailboxId, MailboxSession session) throws Exception {
        MessageResultIterator messages = mailboxManager.getMailbox(mailboxId, session)
            .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session);
        MessageResult result = messages.next();
        return new DefaultMessageBuilder().parseMessage(result.getFullContent().getInputStream());
    }

    private HierarchicalConfiguration<ImmutableNode> config(Map<String, String> configValues) {
        BaseHierarchicalConfiguration config = new BaseHierarchicalConfiguration();
        configValues.forEach(config::addProperty);
        return config;
    }
}