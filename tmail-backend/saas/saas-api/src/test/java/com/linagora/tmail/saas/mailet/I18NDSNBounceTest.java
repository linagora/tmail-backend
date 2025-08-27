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

package com.linagora.tmail.saas.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.mailet.Attribute;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsPatch$;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

import reactor.core.publisher.Mono;

class I18NDSNBounceTest {
    private static final Attribute DELIVERY_ERROR_ATTRIBUTE = Attribute.convertToAttribute("delivery-error", "Delivery error");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2025-09-03T16:12:00Z");
    private static final String LANGUAGE_KEY = "language";
    private static final JmapSettingsKey JMAP_LANGUAGE_KEY = JmapSettingsKey.liftOrThrow(LANGUAGE_KEY);
    private static final String LANGUAGE_EN = "en";
    private static final String LANGUAGE_FR = "fr";
    private static final Username BOB = Username.of("bob@twake.app");
    private static final Username ALICE = Username.of("alice@twake.app");

    private JmapSettingsRepository jmapSettingsRepository;
    private I18NDSNBounce i18NDSNBounce;
    private FakeMailContext mailContext;

    @BeforeEach
    void setUp() throws Exception {
        MailAddress postmaster = new MailAddress("postmaster@twake.app");
        DNSService dnsService = mock(DNSService.class);
        InetAddress localHost = InetAddress.getLocalHost();
        when(dnsService.getLocalHost())
            .thenReturn(localHost);
        when(dnsService.getHostName(localHost))
            .thenReturn("myhost");
        FileSystem fileSystem = FileSystemImpl.forTesting();
        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        mailContext = FakeMailContext.builder().postmaster(postmaster).build();
        UpdatableTickingClock updatableTickingClock = new UpdatableTickingClock(NOW.toInstant());
        i18NDSNBounce = new I18NDSNBounce(dnsService, fileSystem, jmapSettingsRepository, updatableTickingClock);
    }

    @Nested
    class Configuration {
        @Test
        void getAllowedInitParametersShouldReturnTheParameters() {
            assertThat(i18NDSNBounce.getAllowedInitParameters())
                .containsOnly("debug", "passThrough", "attachment", "sender", "prefix", "action", "defaultStatus",
                    "supportedLanguages", "i18nDsnTemplateDirectory", "contentType", "defaultLanguage");
        }

        @Test
        void shouldParseSupportedLanguages() throws Exception {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr,vi")
                .build();

            i18NDSNBounce.init(config);

            Field field = I18NDSNBounce.class.getDeclaredField("supportedLanguages");
            field.setAccessible(true);
            Set<Locale> supported = (Set<Locale>) field.get(i18NDSNBounce);
            assertThat(supported).contains(Locale.ENGLISH, Locale.FRENCH, Locale.forLanguageTag("vi"));
        }

        @Test
        void shouldThrowWhenInvalidSupportedLanguageConfigured() {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "invalid")
                .build();

            assertThatThrownBy(() -> i18NDSNBounce.init(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The provided language 'invalid' can not be parsed to a valid Locale");
        }

        @Test
        void shouldThrowWhenSupportedLanguagesAreNotConfigured() {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .build();

            assertThatThrownBy(() -> i18NDSNBounce.init(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("`supportedLanguages` should not be empty");
        }

        @Test
        void shouldSetI18nDsnTemplateDirectory() throws Exception {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en")
                .setProperty("i18nDsnTemplateDirectory", "classpath://custom/path/")
                .build();

            i18NDSNBounce.init(config);

            Field field = I18NDSNBounce.class.getDeclaredField("i18nDsnTemplateDirectory");
            field.setAccessible(true);
            String dir = (String) field.get(i18NDSNBounce);
            assertThat(dir).isEqualTo("classpath://custom/path/");
        }

        @Test
        void shouldFallbackToDefaultI18nDsnTemplateDirectoryIfNotSet() throws Exception {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en")
                .build();

            i18NDSNBounce.init(config);

            Field field = I18NDSNBounce.class.getDeclaredField("i18nDsnTemplateDirectory");
            field.setAccessible(true);
            String dir = (String) field.get(i18NDSNBounce);
            assertThat(dir).isEqualTo("classpath://eml-template/dsn/");
        }

        @Test
        void shouldSetContentType() throws Exception {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en")
                .setProperty("contentType", "text/html; charset=UTF-8")
                .build();

            i18NDSNBounce.init(config);

            Field field = I18NDSNBounce.class.getDeclaredField("contentType");
            field.setAccessible(true);
            String type = (String) field.get(i18NDSNBounce);
            assertThat(type).isEqualTo("text/html; charset=UTF-8");
        }

        @Test
        void shouldFallbackToTextPlainIfContentTypeNotSet() throws Exception {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en")
                .build();

            i18NDSNBounce.init(config);

            Field field = I18NDSNBounce.class.getDeclaredField("contentType");
            field.setAccessible(true);
            String type = (String) field.get(i18NDSNBounce);
            assertThat(type).isEqualTo("text/plain; charset=UTF-8");
        }

        @Test
        void shouldSetDefaultLanguage() throws Exception {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("defaultLanguage", "fr")
                .build();

            i18NDSNBounce.init(config);

            Field field = I18NDSNBounce.class.getDeclaredField("defaultLanguage");
            field.setAccessible(true);
            Locale lang = (Locale) field.get(i18NDSNBounce);
            assertThat(lang).isEqualTo(Locale.FRENCH);
        }

        @Test
        void shouldFallbackToEnglishIfDefaultLanguageNotSet() throws Exception {
            FakeMailetConfig config = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .build();
            i18NDSNBounce.init(config);
            Field field = I18NDSNBounce.class.getDeclaredField("defaultLanguage");
            field.setAccessible(true);
            Locale lang = (Locale) field.get(i18NDSNBounce);
            assertThat(lang).isEqualTo(Locale.ENGLISH);
        }
    }

    @Nested
    class Language {
        @Test
        void shouldReturnBounceInEnglishWhenSenderLanguageSettingIsEnglish() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // Bob's language setting is English
            Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, LANGUAGE_EN))).block();

            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(BOB.asMailAddress())
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContentType = bodyPart.getContentType();
            String actualContent = (String) bodyPart.getContent();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(actualContentType).isEqualTo("text/plain; charset=UTF-8");

                softly.assertThat(actualContent).contains("MIME-Version: 1.0");
                softly.assertThat(actualContent).contains("Subject: Delivery Status Notification (Failure) for message to %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("From: %s".formatted(BOB.asString()));
                softly.assertThat(actualContent).contains("To: %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("Original subject: Banana power!");
                softly.assertThat(actualContent).contains("Date: Wednesday, 3 September 2025 16:12:00 Z");
                softly.assertThat(actualContent).contains("Error message: Delivery error");
                softly.assertThat(actualContent).contains("Please contact the administrator for more information.");
            });
        }

        @Test
        void shouldReturnBounceInFrenchWhenSenderLanguageSettingIsFrench() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // Bob's language setting is French
            Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, LANGUAGE_FR))).block();

            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(BOB.asMailAddress())
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContentType = bodyPart.getContentType();
            String actualContent = (String) bodyPart.getContent();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(actualContentType).isEqualTo("text/plain; charset=UTF-8");

                softly.assertThat(actualContent).contains("MIME-Version: 1.0");
                softly.assertThat(actualContent).contains("Subject: Notification de Statut de Distribution (Échec) pour le message à %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("De: %s".formatted(BOB.asString()));
                softly.assertThat(actualContent).contains("À: %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("Objet original: Banana power!");
                softly.assertThat(actualContent).contains("Date: mercredi, 3 septembre 2025 16:12:00 Z");
                softly.assertThat(actualContent).contains("Message d’erreur: Delivery error");
                softly.assertThat(actualContent).contains("Veuillez contacter l’administrateur pour plus d’informations.");
            });
        }

        @Test
        void shouldReturnBounceInDefaultLanguageWhenSenderLanguageSettingIsNotSupported() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("defaultLanguage", "en")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // Bob's language setting is not supported
            Mono.from(jmapSettingsRepository.updatePartial(BOB,
                JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, "notSupportedLanguage"))).block();

            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(BOB.asMailAddress())
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContentType = bodyPart.getContentType();
            String actualContent = (String) bodyPart.getContent();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(actualContentType).isEqualTo("text/plain; charset=UTF-8");

                softly.assertThat(actualContent).contains("MIME-Version: 1.0");
                softly.assertThat(actualContent).contains("Subject: Delivery Status Notification (Failure) for message to %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("From: %s".formatted(BOB.asString()));
                softly.assertThat(actualContent).contains("To: %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("Original subject: Banana power!");
                softly.assertThat(actualContent).contains("Date: Wednesday, 3 September 2025 16:12:00 Z");
                softly.assertThat(actualContent).contains("Error message: Delivery error");
                softly.assertThat(actualContent).contains("Please contact the administrator for more information.");
            });
        }

        @Test
        void shouldReturnBounceInDefaultLanguageWhenSenderLanguageSettingIsEmpty() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("defaultLanguage", "en")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // Bob's language setting is not set

            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(BOB.asMailAddress())
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContentType = bodyPart.getContentType();
            String actualContent = (String) bodyPart.getContent();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(actualContentType).isEqualTo("text/plain; charset=UTF-8");

                softly.assertThat(actualContent).contains("MIME-Version: 1.0");
                softly.assertThat(actualContent).contains("Subject: Delivery Status Notification (Failure) for message to %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("From: %s".formatted(BOB.asString()));
                softly.assertThat(actualContent).contains("To: %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("Original subject: Banana power!");
                softly.assertThat(actualContent).contains("Date: Wednesday, 3 September 2025 16:12:00 Z");
                softly.assertThat(actualContent).contains("Error message: Delivery error");
                softly.assertThat(actualContent).contains("Please contact the administrator for more information.");
            });
        }

        @Test
        void shouldThrowWhenSupportedLanguageTemplateIsNotLoadable() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "vi") // Vietnamese template does not exist
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // Bob's language setting is Vietnamese
            Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, "vi"))).block();

            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(BOB.asMailAddress())
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            assertThatThrownBy(() -> i18NDSNBounce.service(mail))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error reading the template file: classpath://eml-template/dsn/dsn-bounce-vi.eml");
        }

        @Test
        void shouldReturnBounceByContentLanguageHeaderWhenExternalSender() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("defaultLanguage", "en")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // GIVEN External sender has no language setting
            // The original mail has a `Content-Language: fr` header
            MailAddress externalSender = MailAddress.of("sender", Domain.of("external.app"));
            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(externalSender)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content")
                    .addHeader("Content-Language", "fr"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContent = (String) bodyPart.getContent();

            // THEN the bounce should be in French
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(actualContent).contains("MIME-Version: 1.0");
                softly.assertThat(actualContent).contains("Subject: Notification de Statut de Distribution (Échec) pour le message à %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("De: %s".formatted(externalSender.asString()));
                softly.assertThat(actualContent).contains("À: %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("Objet original: Banana power!");
                softly.assertThat(actualContent).contains("Date: mercredi, 3 septembre 2025 16:12:00 Z");
                softly.assertThat(actualContent).contains("Message d’erreur: Delivery error");
                softly.assertThat(actualContent).contains("Veuillez contacter l’administrateur pour plus d’informations.");
            });
        }

        @Test
        void shouldReturnBounceByContentLanguageHeaderWhenExternalSenderAndContentLanguageHasRegionalSubTag() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("defaultLanguage", "en")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // GIVEN External sender has no language setting
            // The original mail has a `Content-Language: fr-FR` header which has a regional sub tag
            MailAddress externalSender = MailAddress.of("sender", Domain.of("external.app"));
            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(externalSender)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content")
                    .addHeader("Content-Language", "fr-FR"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContent = (String) bodyPart.getContent();

            // THEN the bounce should be in French
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(actualContent).contains("MIME-Version: 1.0");
                softly.assertThat(actualContent).contains("Subject: Notification de Statut de Distribution (Échec) pour le message à %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("De: %s".formatted(externalSender.asString()));
                softly.assertThat(actualContent).contains("À: %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("Objet original: Banana power!");
                softly.assertThat(actualContent).contains("Date: mercredi, 3 septembre 2025 16:12:00 Z");
                softly.assertThat(actualContent).contains("Message d’erreur: Delivery error");
                softly.assertThat(actualContent).contains("Veuillez contacter l’administrateur pour plus d’informations.");
            });
        }

        @Test
        void shouldReturnBounceByDefaultLanguageWhenExternalSenderAndUnsupportedContentLanguage() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("defaultLanguage", "en")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // GIVEN External sender has no language setting of course, and the original mail has an unsupported Content-Language
            MailAddress externalSender = MailAddress.of("sender", Domain.of("external.app"));
            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(externalSender)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content")
                    .addHeader("Content-Language", "unsupportedLanguage"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContentType = bodyPart.getContentType();
            String actualContent = (String) bodyPart.getContent();

            // THEN the bounce should be in the default language
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(actualContentType).isEqualTo("text/plain; charset=UTF-8");

                softly.assertThat(actualContent).contains("MIME-Version: 1.0");
                softly.assertThat(actualContent).contains("Subject: Delivery Status Notification (Failure) for message to %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("From: %s".formatted(externalSender.asString()));
                softly.assertThat(actualContent).contains("To: %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("Original subject: Banana power!");
                softly.assertThat(actualContent).contains("Date: Wednesday, 3 September 2025 16:12:00 Z");
                softly.assertThat(actualContent).contains("Error message: Delivery error");
                softly.assertThat(actualContent).contains("Please contact the administrator for more information.");
            });
        }

        @Test
        void shouldReturnBounceByDefaultLanguageWhenExternalSenderAndNoContentLanguageHeader() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("defaultLanguage", "en")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // GIVEN External sender has no language setting and the original mail has no Content-Language header
            MailAddress externalSender = MailAddress.of("sender", Domain.of("external.app"));
            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(externalSender)
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContentType = bodyPart.getContentType();
            String actualContent = (String) bodyPart.getContent();

            // THEN the bounce should be in the default language
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(actualContentType).isEqualTo("text/plain; charset=UTF-8");

                softly.assertThat(actualContent).contains("MIME-Version: 1.0");
                softly.assertThat(actualContent).contains("Subject: Delivery Status Notification (Failure) for message to %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("From: %s".formatted(externalSender.asString()));
                softly.assertThat(actualContent).contains("To: %s".formatted(ALICE.asString()));
                softly.assertThat(actualContent).contains("Original subject: Banana power!");
                softly.assertThat(actualContent).contains("Date: Wednesday, 3 September 2025 16:12:00 Z");
                softly.assertThat(actualContent).contains("Error message: Delivery error");
                softly.assertThat(actualContent).contains("Please contact the administrator for more information.");
            });
        }
    }

    @Nested
    class DateGeneratorTest {
        @Test
        void shouldUseTimeOffsetFromDateHeaderIfPossible() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // Bob's language setting is French
            Mono.from(jmapSettingsRepository.updatePartial(BOB,
                JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, LANGUAGE_FR))).block();

            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(BOB.asMailAddress())
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content")
                    .addHeader("Date", "Tue, 2 Sep 2025 14:25:52 +0200"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T12:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContent = (String) bodyPart.getContent();

            // Generated date should be in sender's time offset
            assertThat(actualContent).contains("Date: mercredi, 3 septembre 2025 18:12:00 +02:00");
        }

        @Test
        void shouldFallbackToUTCTimeOffsetWhenInvalidDateHeader() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // Bob's language setting is French
            Mono.from(jmapSettingsRepository.updatePartial(BOB,
                JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, LANGUAGE_FR))).block();

            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(BOB.asMailAddress())
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content")
                    .addHeader("Date", "invalid"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T12:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContent = (String) bodyPart.getContent();

            // Generated date should fall back to the UTC time offset
            assertThat(actualContent).contains("Date: mercredi, 3 septembre 2025 16:12:00 Z");
        }

        @Test
        void shouldFallbackToUTCTimeOffsetWhenNoDateHeader() throws Exception {
            FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
                .mailetName("I18NDSNBounce")
                .mailetContext(mailContext)
                .setProperty("supportedLanguages", "en,fr")
                .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
                .build();
            i18NDSNBounce.init(mailetConfig);

            // Bob's language setting is French
            Mono.from(jmapSettingsRepository.updatePartial(BOB,
                JmapSettingsPatch$.MODULE$.toUpsert(JMAP_LANGUAGE_KEY, LANGUAGE_FR))).block();

            FakeMail mail = FakeMail.builder()
                .name("mail1")
                .sender(BOB.asMailAddress())
                .attribute(DELIVERY_ERROR_ATTRIBUTE)
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSubject("Banana power!")
                    .setText("My content"))
                .recipient(ALICE.asMailAddress())
                .lastUpdated(Date.from(Instant.parse("2025-09-02T12:25:52.000Z")))
                .build();

            i18NDSNBounce.service(mail);

            List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
            assertThat(sentMails).hasSize(1);
            MimeMessage sentMessage = sentMails.getFirst().getMsg();
            MimeMultipart content = (MimeMultipart) sentMessage.getContent();
            BodyPart bodyPart = content.getBodyPart(0);
            String actualContent = (String) bodyPart.getContent();

            // Generated date should fall back to the UTC time offset
            assertThat(actualContent).contains("Date: mercredi, 3 septembre 2025 16:12:00 Z");
        }
    }

    @Test
    void shouldFallbackToAssociatedErrorMessageOfOriginalMailWhenEmptyDeliveryErrorAttribute() throws Exception {
        FakeMailetConfig mailetConfig = FakeMailetConfig.builder()
            .mailetName("I18NDSNBounce")
            .mailetContext(mailContext)
            .setProperty("supportedLanguages", "en,fr")
            .setProperty("defaultLanguage", "en")
            .setProperty("i18nDsnTemplateDirectory", "classpath://eml-template/dsn/")
            .build();
        i18NDSNBounce.init(mailetConfig);

        MailAddress externalSender = MailAddress.of("sender", Domain.of("external.app"));
        FakeMail mail = FakeMail.builder()
            .name("mail1")
            .sender(externalSender)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                .setSubject("Banana power!")
                .setText("My content"))
            .recipient(ALICE.asMailAddress())
            .lastUpdated(Date.from(Instant.parse("2025-09-02T14:25:52.000Z")))
            .build();
        mail.setErrorMessage("Rate limited");

        i18NDSNBounce.service(mail);

        List<FakeMailContext.SentMail> sentMails = mailContext.getSentMails();
        assertThat(sentMails).hasSize(1);
        MimeMessage sentMessage = sentMails.getFirst().getMsg();
        MimeMultipart content = (MimeMultipart) sentMessage.getContent();
        BodyPart bodyPart = content.getBodyPart(0);
        String actualContent = (String) bodyPart.getContent();
        assertThat(actualContent).contains("Error message: Rate limited");
    }

}
