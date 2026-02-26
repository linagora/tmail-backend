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

package com.linagora.tmail.saas.listener;

import static com.linagora.tmail.saas.listener.SaaSSignatureTextFactory.SignatureType.HTML;
import static com.linagora.tmail.saas.listener.SaaSSignatureTextFactory.SignatureType.TEXT;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUtil;
import com.linagora.tmail.james.jmap.settings.LocaleUtil;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class SaaSSignatureTextFactory implements SignatureTextFactory {
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

    public static final String IDENTITY_PROVISION_LISTENER_CONFIGURATION = "identityProvisionListenerConfiguration";

    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSSignatureTextFactory.class);
    private static final String DEFAULT_TEXT_PROPERTY = "defaultText";
    private static final String DEFAULT_LANGUAGE_PROPERTY = "defaultLanguage";
    private static final String DEFAULT_LANGUAGE_PATH = DEFAULT_TEXT_PROPERTY + "." + DEFAULT_LANGUAGE_PROPERTY;
    private static final String TEXT_SIGNATURE_PROPERTY = "textSignature";
    private static final String HTML_SIGNATURE_PROPERTY = "htmlSignature";
    private static final Locale DEFAULT_LANGUAGE = Locale.ENGLISH;
    private static final Pattern SIGNATURE_KEY_PATTERN = Pattern.compile("^defaultText\\.([^.]+)\\.(textSignature|htmlSignature)$");

    private final SaaSAccountRepository saaSAccountRepository;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final Locale defaultLanguage;
    private final I18NSignatureText promotionSignatures;

    @Inject
    public SaaSSignatureTextFactory(SaaSAccountRepository saaSAccountRepository,
                                    JmapSettingsRepository jmapSettingsRepository,
                                    @Named(IDENTITY_PROVISION_LISTENER_CONFIGURATION) HierarchicalConfiguration<ImmutableNode> listenerConfig) {
        this.saaSAccountRepository = saaSAccountRepository;
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.defaultLanguage = Optional.ofNullable(listenerConfig.getString(DEFAULT_LANGUAGE_PATH))
            .map(LocaleUtil::toLocaleStrictly)
            .orElse(DEFAULT_LANGUAGE);
        this.promotionSignatures = asI18NSignatures(listenerConfig);
    }

    @Override
    public Mono<Optional<SignatureText>> forUser(Username username) {
        if (promotionSignatures.isEmpty()) {
            LOGGER.info("i18n signatures are not configured. Returning empty signature for user {}.", username.asString());
            return Mono.just(Optional.empty());
        }

        return isPaying(username)
            .flatMap(payingUser -> {
                if (payingUser) {
                    return Mono.just(Optional.empty());
                }
                return resolveUserPromotionSignature(username)
                    .map(Optional::of);
            });
    }

    private Mono<Boolean> isPaying(Username username) {
        return Mono.from(saaSAccountRepository.getSaaSAccount(username))
            .map(SaaSAccount::isPaying)
            .defaultIfEmpty(false);
    }

    private Mono<SignatureText> resolveUserPromotionSignature(Username username) {
        return resolveUserLanguage(username)
            .map(this::resolveConfiguredPromotionSignature);
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

    private SignatureText resolveConfiguredPromotionSignature(Locale locale) {
        return promotionSignatures.get(locale)
            .orElseGet(() -> {
                LOGGER.info("No signature configured for locale {}. Falling back to default language {}.", locale, defaultLanguage);
                return promotionSignatures.get(defaultLanguage).orElseThrow();
            });
    }

    private I18NSignatureText asI18NSignatures(HierarchicalConfiguration<ImmutableNode> listenerConfig) {
        Map<Locale, Map<SignatureType, String>> signaturesByLocale = collectSignaturesByLocale(listenerConfig);
        if (signaturesByLocale.isEmpty()) {
            LOGGER.warn("No i18n signature configured for IdentityProvisionListener.");
            return I18NSignatureText.empty();
        }

        I18NSignatureText i18NSignatureText = toPromotionSignatures(signaturesByLocale);
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

    private I18NSignatureText toPromotionSignatures(Map<Locale, Map<SignatureType, String>> signaturesByLocale) {
        Map<Locale, SignatureText> promotionSignatures = new HashMap<>();
        signaturesByLocale.forEach((locale, signaturesByType) -> promotionSignatures.put(locale, toPromotionSignature(locale, signaturesByType)));
        return new I18NSignatureText(promotionSignatures);
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

    private SignatureText toPromotionSignature(Locale locale, Map<SignatureType, String> signaturesByType) {
        Preconditions.checkArgument(signaturesByType.containsKey(TEXT),
            "Missing `%s` for promotion language `%s`", TEXT_SIGNATURE_PROPERTY, locale.toLanguageTag());
        Preconditions.checkArgument(signaturesByType.containsKey(HTML),
            "Missing `%s` for promotion language `%s`", HTML_SIGNATURE_PROPERTY, locale.toLanguageTag());

        return new SignatureText(signaturesByType.get(TEXT), signaturesByType.get(HTML));
    }

}
