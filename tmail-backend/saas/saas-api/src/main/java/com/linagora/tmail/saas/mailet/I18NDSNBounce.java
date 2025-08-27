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

import static org.apache.james.transport.mailets.remote.delivery.Bouncer.DELIVERY_ERROR;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.transport.mailets.DSNBounce;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class I18NDSNBounce extends DSNBounce {
    public enum LocaleParseMode {
        STRICT,
        RELAX
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(I18NDSNBounce.class);
    private static final String SUPPORTED_LANGUAGES_PARAMETER = "supportedLanguages";
    private static final String I18N_DSN_TEMPLATE_DIRECTORY_PARAMETER = "i18nDsnTemplateDirectory";
    // Remove the `messageString` parameter as we will load i18n templates instead
    private static final ImmutableSet<String> CONFIGURABLE_PARAMETERS = ImmutableSet.of(
        "debug", "passThrough", "attachment", "sender", "prefix", "action", "defaultStatus",
        SUPPORTED_LANGUAGES_PARAMETER, I18N_DSN_TEMPLATE_DIRECTORY_PARAMETER);
    private static final String DEFAULT_I18N_DSN_TEMPLATE_DIRECTORY = "classpath://eml-template/dsn/";
    private static final Locale DEFAULT_LANGUAGE = Locale.ENGLISH;

    private final FileSystem fileSystem;
    private final JmapSettingsRepository jmapSettingsRepository;
    private Set<Locale> supportedLanguages;
    private String i18nDsnTemplateDirectory;

    @Inject
    public I18NDSNBounce(DNSService dns,
                         FileSystem fileSystem,
                         JmapSettingsRepository jmapSettingsRepository) {
        super(dns);
        this.fileSystem = fileSystem;
        this.jmapSettingsRepository = jmapSettingsRepository;
    }

    @Override
    public void init() throws MessagingException {
        super.init();
        supportedLanguages = asSupportedLanguages(getInitParameter(SUPPORTED_LANGUAGES_PARAMETER));
        i18nDsnTemplateDirectory = getInitParameter(I18N_DSN_TEMPLATE_DIRECTORY_PARAMETER, DEFAULT_I18N_DSN_TEMPLATE_DIRECTORY);
    }

    @Override
    public Set<String> getAllowedInitParameters() {
        return CONFIGURABLE_PARAMETERS;
    }

    @Override
    protected MimeBodyPart createTextMsg(Mail originalMail) throws MessagingException {
        StringBuilder builder = new StringBuilder();

        builder.append(i18nBounceMessage(originalMail)).append(LINE_BREAK);

        Optional.ofNullable(originalMail.getMessage().getSubject())
            .ifPresent(subject -> builder.append("Original email subject: ")
                .append(subject)
                .append(LINE_BREAK)
                .append(LINE_BREAK));
        builder.append(action.asString()).append(" recipient(s):").append(LINE_BREAK);
        builder.append(originalMail.getRecipients()
            .stream()
            .map(MailAddress::asString)
            .collect(Collectors.joining(", ")));
        builder.append(LINE_BREAK).append(LINE_BREAK);
        if (action.shouldIncludeDiagnostic()) {
            Optional<String> deliveryError = AttributeUtils.getValueAndCastFromMail(originalMail, DELIVERY_ERROR, String.class);

            deliveryError.or(() -> Optional.ofNullable(originalMail.getErrorMessage()))
                .ifPresent(message -> {
                    builder.append("Error message:").append(LINE_BREAK);
                    builder.append(message).append(LINE_BREAK);
                    builder.append(LINE_BREAK);
                });
        }
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setText(builder.toString());
        return bodyPart;
    }

    private String i18nBounceMessage(Mail originalMail) {
        getSenderLocaleFromSettings(originalMail);
        // TODO Use fileSystem to load eml template from i18nDsnTemplateDirectory by the resolved locale
        // e.g. if resolved locale is `fr`, load `i18nDsnTemplateDirectory/dsn-bounce-fr.eml`
        // e.g. if resolved locale is `en`, load `i18nDsnTemplateDirectory/dsn-bounce-en.eml`
        // If the resolved locale is not supported, fallback to load the english template, with a WARN log

        // TODO then user mustache to render the template with variables
        return "todo";
    }

    private Mono<Locale> getSenderLocaleFromSettings(Mail originalMail) {
        return Mono.justOrEmpty(originalMail.getMaybeSender().asOptional())
            .map(Username::fromMailAddress)
            .flatMap(username -> Mono.from(jmapSettingsRepository.get(username))
                .map(jmapSettings -> OptionConverters.toJava(jmapSettings.language())
                    .map(language -> toLocale(language, LocaleParseMode.RELAX))
                    .orElse(DEFAULT_LANGUAGE)));
    }

    private Set<Locale> asSupportedLanguages(String supportedLanguages) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(supportedLanguages), "`supportedLanguages` should not be empty");

        return Splitter.on(",")
            .omitEmptyStrings()
            .trimResults()
            .splitToStream(supportedLanguages)
            .map(language -> toLocale(language, LocaleParseMode.STRICT))
            .collect(ImmutableSet.toImmutableSet());
    }

    @VisibleForTesting
    public static Locale toLocale(String language, LocaleParseMode parseMode) {
        if (Arrays.stream(Locale.getISOLanguages())
            .anyMatch(localeLanguage -> localeLanguage.equalsIgnoreCase(language))) {
            return Locale.forLanguageTag(language);
        }

        return switch (parseMode) {
            case RELAX -> {
                LOGGER.warn("The provided language '{}' can not be parsed to a valid Locale. Falling back to default locale '{}'", language, DEFAULT_LANGUAGE);
                yield DEFAULT_LANGUAGE;
            }
            case STRICT -> throw new IllegalArgumentException("The provided language '" + language + "' can not be parsed to a valid Locale.");
        };
    }

}
