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

package com.linagora.tmail.james.jmap.event;

import static com.linagora.tmail.james.jmap.event.DefaultSignatureTextFactory.SignatureType.HTML;
import static com.linagora.tmail.james.jmap.event.DefaultSignatureTextFactory.SignatureType.TEXT;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUtil;
import com.linagora.tmail.james.jmap.settings.LocaleUtil;

import reactor.core.publisher.Mono;

public class DefaultSignatureTextFactory implements SignatureTextFactory {
    record I18NSignatureText(Map<Locale, SignatureText> i18nSignatures) {
        I18NSignatureText {
            i18nSignatures = ImmutableMap.copyOf(i18nSignatures);
        }

        static I18NSignatureText empty() {
            return new I18NSignatureText(Map.of());
        }

        boolean isEmpty() {
            return i18nSignatures.isEmpty();
        }

        boolean contains(Locale locale) {
            return i18nSignatures.containsKey(locale);
        }

        Optional<SignatureText> get(Locale locale) {
            return Optional.ofNullable(i18nSignatures.get(locale));
        }
    }

    record SignatureConfigEntry(Locale locale, SignatureType signatureType, String value) {

    }

    enum SignatureType {
        TEXT,
        HTML;

        static SignatureType fromPropertyName(String propertyName) {
            return switch (propertyName) {
                case TEXT_SIGNATURE_PROPERTY -> TEXT;
                case HTML_SIGNATURE_PROPERTY -> HTML;
                default -> throw new IllegalArgumentException("Unsupported signature property `%s`".formatted(propertyName));
            };
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSignatureTextFactory.class);
    private static final String DEFAULT_TEXT_PROPERTY = "defaultText";
    private static final String DEFAULT_LANGUAGE_PROPERTY = "defaultLanguage";
    private static final String DEFAULT_LANGUAGE_PATH = DEFAULT_TEXT_PROPERTY + "." + DEFAULT_LANGUAGE_PROPERTY;
    private static final String TEXT_SIGNATURE_PROPERTY = "textSignature";
    private static final String HTML_SIGNATURE_PROPERTY = "htmlSignature";
    private static final Locale DEFAULT_LANGUAGE = Locale.ENGLISH;
    private static final Pattern SIGNATURE_KEY_PATTERN = Pattern.compile("^defaultText\\.([^.]+)\\.(textSignature|htmlSignature)$");

    private final JmapSettingsRepository jmapSettingsRepository;
    private final ApplyWhenFilter applyWhenFilter;
    private final Locale defaultLanguage;
    private final I18NSignatureText configuredSignatures;

    public DefaultSignatureTextFactory(JmapSettingsRepository jmapSettingsRepository,
                                       HierarchicalConfiguration<ImmutableNode> listenerConfig,
                                       ApplyWhenFilter applyWhenFilter) {
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.applyWhenFilter = applyWhenFilter;
        this.defaultLanguage = Optional.ofNullable(listenerConfig.getString(DEFAULT_LANGUAGE_PATH))
            .map(LocaleUtil::toLocaleStrictly)
            .orElse(DEFAULT_LANGUAGE);
        this.configuredSignatures = asI18NSignatures(listenerConfig);
    }

    @VisibleForTesting
    public DefaultSignatureTextFactory(JmapSettingsRepository jmapSettingsRepository,
                                       HierarchicalConfiguration<ImmutableNode> listenerConfig) {
        this(jmapSettingsRepository, listenerConfig, new ApplyWhenFilter.Always());
    }

    @Override
    public Mono<Optional<SignatureText>> forUser(Username username) {
        if (configuredSignatures.isEmpty()) {
            LOGGER.info("i18n signatures are not configured. Returning empty signature for user {}.", username.asString());
            return Mono.just(Optional.empty());
        }

        return applyWhenFilter.isEligible(username)
            .flatMap(isEligible -> {
                if (!isEligible) {
                    return Mono.just(Optional.empty());
                }
                return resolveUserConfiguredSignature(username)
                    .map(Optional::of);
            });
    }

    private Mono<SignatureText> resolveUserConfiguredSignature(Username username) {
        return resolveUserLanguage(username)
            .map(this::resolveConfiguredSignature);
    }

    private Mono<Locale> resolveUserLanguage(Username username) {
        return Mono.from(jmapSettingsRepository.get(username))
            .map(jmapSettings -> JmapSettingsUtil.parseLocaleFromSettings(jmapSettings, defaultLanguage)
                .orElse(defaultLanguage))
            .defaultIfEmpty(defaultLanguage)
            .onErrorResume(error -> {
                LOGGER.error("Error while retrieving language settings for user {}. Falling back to default language {}.", username.asString(), defaultLanguage, error);
                return Mono.just(defaultLanguage);
            });
    }

    private SignatureText resolveConfiguredSignature(Locale locale) {
        return configuredSignatures.get(locale)
            .orElseGet(() -> {
                LOGGER.info("No signature configured for locale {}. Falling back to default language {}.", locale, defaultLanguage);
                return configuredSignatures.get(defaultLanguage).orElseThrow();
            });
    }

    private I18NSignatureText asI18NSignatures(HierarchicalConfiguration<ImmutableNode> listenerConfig) {
        Map<Locale, Map<SignatureType, String>> signaturesByLocale = collectSignaturesByLocale(listenerConfig);
        if (signaturesByLocale.isEmpty()) {
            LOGGER.warn("No i18n signature configured for IdentityProvisionListener.");
            return I18NSignatureText.empty();
        }

        I18NSignatureText i18NSignatureText = toConfiguredSignatures(signaturesByLocale);
        Preconditions.checkArgument(i18NSignatureText.contains(defaultLanguage),
            "`defaultLanguage` should be one of configured `defaultText` languages");
        return i18NSignatureText;
    }

    private Map<Locale, Map<SignatureType, String>> collectSignaturesByLocale(HierarchicalConfiguration<ImmutableNode> listenerConfig) {
        Map<Locale, Map<SignatureType, String>> signaturesByLocale = new HashMap<>();
        listenerConfig.getKeys(DEFAULT_TEXT_PROPERTY)
            .forEachRemaining(key -> parseSignatureConfigEntry(listenerConfig, key)
                .ifPresent(signatureConfig -> signaturesByLocale.computeIfAbsent(signatureConfig.locale(), any -> new EnumMap<>(SignatureType.class))
                    .put(signatureConfig.signatureType(), signatureConfig.value())));
        return signaturesByLocale;
    }

    private I18NSignatureText toConfiguredSignatures(Map<Locale, Map<SignatureType, String>> signaturesByLocale) {
        Map<Locale, SignatureText> signatures = new HashMap<>();
        signaturesByLocale.forEach((locale, signaturesByType) -> signatures.put(locale, toSignatureText(locale, signaturesByType)));
        return new I18NSignatureText(signatures);
    }

    private Optional<SignatureConfigEntry> parseSignatureConfigEntry(HierarchicalConfiguration<ImmutableNode> listenerConfig, String key) {
        Matcher matcher = SIGNATURE_KEY_PATTERN.matcher(key);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        Locale locale = LocaleUtil.toLocaleStrictly(matcher.group(1));
        String value = listenerConfig.getString(key);

        return Optional.of(new SignatureConfigEntry(locale, SignatureType.fromPropertyName(matcher.group(2)), value));
    }

    private SignatureText toSignatureText(Locale locale, Map<SignatureType, String> signaturesByType) {
        Preconditions.checkArgument(signaturesByType.containsKey(TEXT),
            "Missing `%s` for language `%s`", TEXT_SIGNATURE_PROPERTY, locale.toLanguageTag());
        Preconditions.checkArgument(signaturesByType.containsKey(HTML),
            "Missing `%s` for language `%s`", HTML_SIGNATURE_PROPERTY, locale.toLanguageTag());

        return new SignatureText(signaturesByType.get(TEXT), signaturesByType.get(HTML));
    }
}
