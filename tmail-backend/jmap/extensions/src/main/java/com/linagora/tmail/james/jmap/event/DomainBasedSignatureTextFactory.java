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

import java.util.Locale;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUtil;

import reactor.core.publisher.Mono;

public class DomainBasedSignatureTextFactory implements SignatureTextFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainBasedSignatureTextFactory.class);
    private static final Locale DEFAULT_LANGUAGE = Locale.ENGLISH;

    private final DomainSignatureTemplateRepository repository;
    private final JmapSettingsRepository jmapSettingsRepository;

    @Inject
    public DomainBasedSignatureTextFactory(DomainSignatureTemplateRepository repository,
                                           JmapSettingsRepository jmapSettingsRepository) {
        this.repository = repository;
        this.jmapSettingsRepository = jmapSettingsRepository;
    }

    @Override
    public Mono<Optional<SignatureText>> forUser(Username username) {
        return username.getDomainPart()
            .map(domain -> resolveForDomain(username, domain))
            .orElseGet(() -> {
                LOGGER.debug("Username {} has no domain part. Returning empty signature.", username.asString());
                return Mono.just(Optional.empty());
            });
    }

    private Mono<Optional<SignatureText>> resolveForDomain(Username username, Domain domain) {
        return repository.get(domain)
            .flatMap(templateOpt -> templateOpt
                .map(template -> resolveSignature(username, template))
                .orElseGet(() -> {
                    LOGGER.debug("No domain signature template found for domain {}. Returning empty.", domain.asString());
                    return Mono.just(Optional.empty());
                }));
    }

    private Mono<Optional<SignatureText>> resolveSignature(Username username, DomainSignatureTemplate template) {
        return resolveUserLocale(username)
            .map(locale -> template.forLocale(locale, DEFAULT_LANGUAGE));
    }

    private Mono<Locale> resolveUserLocale(Username username) {
        return Mono.from(jmapSettingsRepository.get(username))
            .map(settings -> JmapSettingsUtil.parseLocaleFromSettings(settings, DEFAULT_LANGUAGE)
                .orElse(DEFAULT_LANGUAGE))
            .defaultIfEmpty(DEFAULT_LANGUAGE)
            .onErrorResume(error -> {
                LOGGER.error("Error retrieving language for user {}. Falling back to {}.", username.asString(), DEFAULT_LANGUAGE, error);
                return Mono.just(DEFAULT_LANGUAGE);
            });
    }
}
