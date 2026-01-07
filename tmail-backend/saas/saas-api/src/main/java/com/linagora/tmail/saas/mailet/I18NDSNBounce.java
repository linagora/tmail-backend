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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
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

import com.github.fge.lambdas.Throwing;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.settings.JmapSettings;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUtil;
import com.linagora.tmail.james.jmap.settings.LocaleUtil;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * <p>
 * Generates a Delivery Status Notification (DSN) with internationalization (i18n) support, extending {@link DSNBounce}.
 * This mailet allows sending bounce messages in the recipient's preferred language, using configurable templates and content types.
 * </p>
 *
 * <p>
 * The bounce message is generated from a mustache template, selected based on the recipient's language preference (from JMAP settings or Content-Language header),
 * and falls back to a default language if not available. The template variables include sender, recipients, subject, date, and error message.
 * </p>
 *
 * <p>
 * Supported configuration parameters:
 * <ul>
 *   <li><b>debug</b>: true or false, default=false</li>
 *   <li><b>passThrough</b>: true or false, default=true</li>
 *   <li><b>attachment</b>: message, heads or none, default=message</li>
 *   <li><b>sender</b>: an address or postmaster or sender or unaltered, default=postmaster</li>
 *   <li><b>prefix</b>: optional subject prefix prepended to the original message</li>
 *   <li><b>action</b>: failed, delayed, delivered, expanded or relayed, default=failed</li>
 *   <li><b>defaultStatus</b>: SMTP status code, default=unknown</li>
 *   <li><b>supportedLanguages</b>: comma-separated list of supported language tags (e.g. "en,fr,vi")</li>
 *   <li><b>i18nDsnTemplateDirectory</b>: directory path for i18n DSN templates (default: classpath://eml-template/dsn/)</li>
 *   <li><b>contentType</b>: MIME content type for the bounce message (default: text/plain; charset=UTF-8)</li>
 *   <li><b>defaultLanguage</b>: default language tag used if no preference is found (default: "en")</li>
 * </ul>
 * </p>
 *
 * <p>
 * Example configuration:
 * </p>
 * <pre>
 * {@code
 * <mailet match="All" class="com.linagora.tmail.saas.mailet.I18NDSNBounce">
 *   <sender>postmaster</sender>
 *   <attachment>message</attachment>
 *   <passThrough>true</passThrough>
 *   <debug>false</debug>
 *   <action>failed</action>
 *   <defaultStatus>5.0.0</defaultStatus>
 *   <supportedLanguages>en,fr,de</supportedLanguages>
 *   <i18nDsnTemplateDirectory>classpath://eml-template/dsn/</i18nDsnTemplateDirectory>
 *   <contentType>text/plain; charset=UTF-8</contentType>
 *   <defaultLanguage>en</defaultLanguage>
 * </mailet>
 * }
 * </pre>
 *
 * <p>
 * The template file is selected as "dsn-bounce-<language>.eml" from the configured directory. If the recipient's language is not supported, the default language is used.
 * </p>
 *
 * @see DSNBounce
 */
public class I18NDSNBounce extends DSNBounce {
    private static final Logger LOGGER = LoggerFactory.getLogger(I18NDSNBounce.class);
    private static final String SUPPORTED_LANGUAGES_PARAMETER = "supportedLanguages";
    private static final String I18N_DSN_TEMPLATE_DIRECTORY_PARAMETER = "i18nDsnTemplateDirectory";
    private static final String CONTENT_TYPE_PARAMETER = "contentType";
    private static final String DEFAULT_LANGUAGE_PARAMETER = "defaultLanguage";
    // Remove the `messageString` parameter as we will load i18n templates instead
    private static final ImmutableSet<String> CONFIGURABLE_PARAMETERS = ImmutableSet.of(
        "debug", "passThrough", "attachment", "sender", "prefix", "action", "defaultStatus",
        SUPPORTED_LANGUAGES_PARAMETER, I18N_DSN_TEMPLATE_DIRECTORY_PARAMETER, CONTENT_TYPE_PARAMETER, DEFAULT_LANGUAGE_PARAMETER);
    private static final String DEFAULT_I18N_DSN_TEMPLATE_DIRECTORY = "classpath://eml-template/dsn/";
    private static final Locale DEFAULT_LANGUAGE = Locale.ENGLISH;
    private static final String DEFAULT_CONTENT_TYPE = "text/plain; charset=UTF-8";
    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();
    private static final String MUSTACHE_SENDER_PROPERTY = "SENDER";
    private static final String MUSTACHE_RECIPIENTS_PROPERTY = "RECIPIENTS";
    private static final String MUSTACHE_SUBJECT_PROPERTY = "SUBJECT";
    private static final String MUSTACHE_DATE_PROPERTY = "DATE";
    private static final String MUSTACHE_ERROR_MESSAGE_PROPERTY = "ERROR_MESSAGE";

    private final FileSystem fileSystem;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final LoadingCache<String, String> templateCache;
    private final Clock clock;
    private Set<Locale> supportedLanguages;
    private String i18nDsnTemplateDirectory;
    private String contentType;
    private Locale defaultLanguage;

    @Inject
    public I18NDSNBounce(DNSService dns,
                         FileSystem fileSystem,
                         JmapSettingsRepository jmapSettingsRepository,
                         Clock clock) {
        super(dns);
        this.fileSystem = fileSystem;
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.clock = clock;
        this.templateCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .build(new CacheLoader<>() {
                @Override
                public String load(String fileName) throws Exception {
                    return loadTemplateFromFile(fileName);
                }
            });
    }

    @Override
    public void init() throws MessagingException {
        super.init();
        supportedLanguages = asSupportedLanguages(getInitParameter(SUPPORTED_LANGUAGES_PARAMETER));
        i18nDsnTemplateDirectory = getInitParameter(I18N_DSN_TEMPLATE_DIRECTORY_PARAMETER, DEFAULT_I18N_DSN_TEMPLATE_DIRECTORY);
        contentType = getInitParameter(CONTENT_TYPE_PARAMETER, DEFAULT_CONTENT_TYPE);
        defaultLanguage = Optional.ofNullable(getInitParameter(DEFAULT_LANGUAGE_PARAMETER))
            .map(LocaleUtil::toLocaleStrictly)
            .orElse(DEFAULT_LANGUAGE);
    }

    @Override
    public Set<String> getAllowedInitParameters() {
        return CONFIGURABLE_PARAMETERS;
    }

    @Override
    protected MimeBodyPart createTextMsg(Mail originalMail) throws MessagingException {
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setContent(i18nBounceMessage(originalMail), contentType);
        return bodyPart;
    }

    private String i18nBounceMessage(Mail originalMail) {
        return getSenderLocale(originalMail)
            .flatMap(senderLocale -> Mono.fromCallable(() -> prepareTemplateVariables(originalMail, senderLocale))
                .map(templateVariables -> generateDsnBodyPart(senderLocale, templateVariables)))
            .subscribeOn(Schedulers.boundedElastic())
            .block();
    }

    private Mono<Locale> getSenderLocale(Mail originalMail) {
        return sender(originalMail)
            .flatMap(sender -> Mono.from(jmapSettingsRepository.get(sender))
                .map(jmapSettings -> parseLocaleFromSettings(jmapSettings)
                    .orElseGet(Throwing.supplier(() -> {
                        LOGGER.info("No language setting found for user {}, falling back to the Content-Language header", sender);
                        return parseLocaleByContentLanguageHeader(originalMail);
                    })))
                .switchIfEmpty(Mono.defer(() -> {
                    LOGGER.info("No settings found for user {}, falling back to the Content-Language header", sender);
                    return Mono.fromCallable(() -> parseLocaleByContentLanguageHeader(originalMail));
                }))
                .onErrorResume(throwable -> {
                    LOGGER.error("Error getting sender {} locale. Falling back to default locale {}", sender.asString(), defaultLanguage, throwable);
                    return Mono.just(defaultLanguage);
                }))
            .defaultIfEmpty(defaultLanguage);
    }

    private Mono<Username> sender(Mail originalMail) {
        return Mono.justOrEmpty(originalMail.getMaybeSender().asOptional())
            .map(Username::fromMailAddress);
    }

    private Optional<Locale> parseLocaleFromSettings(JmapSettings jmapSettings) {
        return JmapSettingsUtil.parseLocaleFromSettings(jmapSettings, defaultLanguage);
    }

    private Locale parseLocaleByContentLanguageHeader(Mail originalMail) throws MessagingException, IOException {
        return LocaleUtil.toLocaleRelaxedly(getContentLanguageFromMail(originalMail), defaultLanguage);
    }

    private String getContentLanguageFromMail(Mail mail) throws MessagingException, IOException {
        return getContentLanguageFromPart(mail.getMessage());
    }

    private String getContentLanguageFromPart(Part part) throws MessagingException, IOException {
        String[] contentLanguageHeaders = part.getHeader("Content-Language");
        if (contentLanguageHeaders != null && contentLanguageHeaders.length > 0) {
            String headerValue = contentLanguageHeaders[0];
            if (headerValue != null && headerValue.length() >= 2) {
                return headerValue.substring(0, 2); // Return only the base language subtag
            }
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                String contentLanguage = getContentLanguageFromPart(bodyPart);
                if (contentLanguage != null) {
                    return contentLanguage;
                }
            }
        }
        return null;
    }

    private Set<Locale> asSupportedLanguages(String supportedLanguages) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(supportedLanguages), "`supportedLanguages` should not be empty");

        return Splitter.on(",")
            .omitEmptyStrings()
            .trimResults()
            .splitToStream(supportedLanguages)
            .map(LocaleUtil::toLocaleStrictly)
            .collect(ImmutableSet.toImmutableSet());
    }

    private String generateDsnBodyPart(Locale locale, Map<String, String> variables) {
        String templateFilePath = URI.create(i18nDsnTemplateDirectory)
            .resolve(evaluateMailTemplateFilename(locale))
            .toString();

        try {
            String template = templateCache.get(templateFilePath);
            return renderMustache(new StringReader(template), variables);
        } catch (Exception e) {
            throw new RuntimeException("Error reading the template file: " + templateFilePath, e);
        }
    }

    private String evaluateMailTemplateFilename(Locale senderLanguage) {
        if (supportedLanguages.contains(senderLanguage)) {
            return String.format("dsn-bounce-%s.eml", senderLanguage.getLanguage());
        }
        LOGGER.warn("Locale {} is not supported, falling back to the default {} locale", senderLanguage, defaultLanguage);
        return String.format("dsn-bounce-%s.eml", defaultLanguage.getLanguage());
    }

    private String loadTemplateFromFile(String templateFilePath) throws IOException {
        try (InputStream inputStream = fileSystem.getResource(templateFilePath)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String renderMustache(Reader templateReader, Map<String, String> variables) {
        Mustache mustache = MUSTACHE_FACTORY.compile(templateReader, "dsn-bounce");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, variables);
        return writer.toString();
    }

    private Map<String, String> prepareTemplateVariables(Mail originalMail, Locale senderLocale) throws MessagingException {
        Map<String, String> variables = new HashMap<>();
        variables.put(MUSTACHE_SENDER_PROPERTY, Optional.ofNullable(originalMail.getMaybeSender().asString())
            .orElse(""));
        variables.put(MUSTACHE_SUBJECT_PROPERTY, originalMail.getMessage().getSubject());
        variables.put(MUSTACHE_RECIPIENTS_PROPERTY, originalMail.getRecipients()
            .stream()
            .map(MailAddress::asString)
            .collect(Collectors.joining(", ")));
        variables.put(MUSTACHE_DATE_PROPERTY, evaluateCurrentDate(originalMail, senderLocale));
        variables.put(MUSTACHE_ERROR_MESSAGE_PROPERTY, AttributeUtils.getValueAndCastFromMail(originalMail, DELIVERY_ERROR, String.class)
            .or(() -> Optional.ofNullable(originalMail.getErrorMessage()))
            .orElse(""));

        return variables;
    }

    private String evaluateCurrentDate(Mail originalMail, Locale locale) {
        ZoneOffset zoneFromHeader = getZoneOffset(originalMail);

        return ZonedDateTime.now(clock)
            .withZoneSameInstant(zoneFromHeader)
            .format(localizedDateFormatter(locale));
    }

    private ZoneOffset getZoneOffset(Mail originalMail) {
        try {
            String[] headers = originalMail.getMessage().getHeader("Date");
            if (headers != null && headers.length > 0) {
                ZonedDateTime parsed = ZonedDateTime.parse(headers[0], DateTimeFormatter.RFC_1123_DATE_TIME);
                return parsed.getOffset();
            }
        } catch (Exception e) {
            LOGGER.info("Failed to parse Date header, falling back to UTC", e);
        }
        return ZoneOffset.UTC;
    }

    @VisibleForTesting
    public static DateTimeFormatter localizedDateFormatter(Locale locale) {
        return DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy HH:mm:ss z", locale);
    }

}
