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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUtil;
import com.linagora.tmail.james.jmap.settings.LocaleUtil;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class WelcomeListener implements EventListener.ReactiveGroupEventListener {
    public record I18nConfig(Set<Locale> supportedLanguages, String i18nWelcomeTemplateDirectory, Locale defaultLanguage) {
        private static final String SUPPORTED_LANGUAGES_PARAMETER = "supportedLanguages";
        private static final String I18N_WELCOME_TEMPLATE_DIRECTORY_PARAMETER = "i18nWelcomeTemplateDirectory";
        private static final String DEFAULT_LANGUAGE_PARAMETER = "defaultLanguage";
        private static final String DEFAULT_I18N_WELCOME_TEMPLATE_DIRECTORY = "classpath://eml-template/welcome/";
        private static final Locale DEFAULT_LANGUAGE = Locale.ENGLISH;

        public static Optional<I18nConfig> from(HierarchicalConfiguration<ImmutableNode> configuration) {
            return Optional.ofNullable(configuration.getString(SUPPORTED_LANGUAGES_PARAMETER))
                .map(languages -> new I18nConfig(asSupportedLanguages(languages), getTemplateDirectory(configuration), getDefaultLanguage(configuration)));
        }

        private static Set<Locale> asSupportedLanguages(String supportedLanguages) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(supportedLanguages), "`supportedLanguages` should not be empty");

            return Splitter.on(",")
                .omitEmptyStrings()
                .trimResults()
                .splitToStream(supportedLanguages)
                .map(LocaleUtil::toLocaleStrictly)
                .collect(ImmutableSet.toImmutableSet());
        }

        private static String getTemplateDirectory(HierarchicalConfiguration<ImmutableNode> configuration) {
            return Optional.ofNullable(configuration.getString(I18N_WELCOME_TEMPLATE_DIRECTORY_PARAMETER))
                .map(dir -> {
                    Preconditions.checkArgument(!Strings.isNullOrEmpty(dir), "`i18nWelcomeTemplateDirectory` should not be empty");
                    return dir;
                })
                .orElse(DEFAULT_I18N_WELCOME_TEMPLATE_DIRECTORY);
        }

        private static Locale getDefaultLanguage(HierarchicalConfiguration<ImmutableNode> configuration) {
            return Optional.ofNullable(configuration.getString(DEFAULT_LANGUAGE_PARAMETER))
                .map(LocaleUtil::toLocaleStrictly)
                .orElse(DEFAULT_LANGUAGE);
        }
    }

    private static final WelcomeListenerGroup GROUP = new WelcomeListenerGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(WelcomeListener.class);
    private static final String LEGACY_EML_PARAMETER = "eml";

    public static class WelcomeListenerGroup extends Group {

    }

    private final MailboxManager mailboxManager;
    private final FileSystem fileSystem;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final LoadingCache<String, Message> templateCache;
    private final Optional<I18nConfig> i18nConfig;
    private final Optional<String> legacyEmlLocation;

    @Inject
    public WelcomeListener(MailboxManager mailboxManager,
                           FileSystem fileSystem,
                           JmapSettingsRepository jmapSettingsRepository,
                           HierarchicalConfiguration<ImmutableNode> configuration) {
        this.mailboxManager = mailboxManager;
        this.fileSystem = fileSystem;
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.templateCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .build(new CacheLoader<>() {
                @Override
                public Message load(String fileName) throws Exception {
                    return loadTemplateFromFile(fileName);
                }
            });

        this.i18nConfig = I18nConfig.from(configuration);
        this.legacyEmlLocation = legacyEmlLocation(i18nConfig, configuration);
    }

    private Optional<String> legacyEmlLocation(Optional<I18nConfig> i18nConfig, HierarchicalConfiguration<ImmutableNode> configuration) {
        return i18nConfig
            .map(i18nEnabled -> Optional.<String>empty())
            .orElseGet(() -> Optional.of(getLegacyEmlLocation(configuration)));
    }

    private String getLegacyEmlLocation(HierarchicalConfiguration<ImmutableNode> configuration) {
        String legacyEmlLocation = configuration.getString(LEGACY_EML_PARAMETER);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(legacyEmlLocation),
            "`eml` must be present when i18n is not configured");
        return legacyEmlLocation;
    }

    @Override
    public boolean isHandling(Event event) {
        if (event instanceof MailboxEvents.MailboxAdded mailboxAdded) {
            return mailboxAdded.getMailboxPath().getName().equalsIgnoreCase(MailboxConstants.INBOX)
                && mailboxAdded.getMailboxPath().getUser().equals(event.getUsername());
        }
        return false;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (isHandling(event)) {
            MailboxEvents.MailboxAdded mailboxAdded = (MailboxEvents.MailboxAdded) event;
            MailboxSession session = mailboxManager.createSystemSession(event.getUsername());

            return Mono.from(mailboxManager.getMailboxReactive(mailboxAdded.getMailboxId(), session))
                .flatMap(mailbox -> retrieveAppendCommand(event.getUsername())
                    .flatMap(appendCommand -> Mono.from(mailbox.appendMessageReactive(
                        appendCommand, session))))
                .then()
                .subscribeOn(Schedulers.boundedElastic());
        }
        return Mono.empty();
    }

    private Message loadTemplateFromFile(String templateFilePath) throws IOException {
        try (InputStream inputStream = fileSystem.getResource(templateFilePath)) {
            DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
            defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
            return defaultMessageBuilder.parseMessage(inputStream);
        }
    }

    private Mono<MessageManager.AppendCommand> retrieveAppendCommand(Username username) {
        return i18nConfig
            .map(i18n -> getUserLocale(username, i18n.defaultLanguage())
                .map(locale -> buildAppendCommand(resolveI18nTemplatePath(i18n, locale))))
            .orElseGet(() -> Mono.fromCallable(() -> buildAppendCommand(legacyEmlLocation.orElseThrow())));
    }

    private Mono<Locale> getUserLocale(Username username, Locale defaultLanguage) {
        return Mono.from(jmapSettingsRepository.get(username))
            .map(jmapSettings -> JmapSettingsUtil.parseLocaleFromSettings(jmapSettings, defaultLanguage)
                .orElseGet(() -> {
                    LOGGER.info("No language setting found for user {}, falling back to default locale {}", username, defaultLanguage);
                    return defaultLanguage;
                }))
            .switchIfEmpty(Mono.defer(() -> {
                LOGGER.info("No settings found for user {}, falling back to default locale {}", username, defaultLanguage);
                return Mono.just(defaultLanguage);
            }))
            .onErrorResume(throwable -> {
                LOGGER.error("Error getting user {} locale. Falling back to default locale {}", username.asString(), defaultLanguage, throwable);
                return Mono.just(defaultLanguage);
            });
    }

    private MessageManager.AppendCommand buildAppendCommand(String templateFilePath) {
        try {
            Message message = templateCache.get(templateFilePath);
            return MessageManager.AppendCommand.builder()
                .build(Message.Builder.of()
                    .copy(message)
                    .setDate(new Date())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Error reading the template file: " + templateFilePath, e);
        }
    }

    private String resolveI18nTemplatePath(I18nConfig config, Locale locale) {
        return URI.create(config.i18nWelcomeTemplateDirectory())
            .resolve(evaluateMailTemplateFilename(locale, config.supportedLanguages(), config.defaultLanguage()))
            .toString();
    }

    private String evaluateMailTemplateFilename(Locale userLocale, Set<Locale> supportedLanguages, Locale defaultLanguage) {
        if (supportedLanguages.contains(userLocale)) {
            return String.format("welcome-%s.eml", userLocale.getLanguage());
        }

        LOGGER.warn("Locale {} is not supported, falling back to the default {} locale", userLocale, defaultLanguage);
        return String.format("welcome-%s.eml", defaultLanguage.getLanguage());
    }
}
