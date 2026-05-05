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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;
import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsPatch$;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

import reactor.core.publisher.Mono;

class DomainBasedSignatureTextFactoryTest {

    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username BOB_NO_DOMAIN = Username.of("bob");
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final JmapSettingsKey LANGUAGE_KEY = JmapSettingsKey.liftOrThrow("language");
    private static final SignatureText EN_SIG = new SignatureText("en text", "<p>en html</p>");
    private static final SignatureText FR_SIG = new SignatureText("fr text", "<p>fr html</p>");

    private MemoryDomainSignatureTemplateRepository repository;
    private MemoryJmapSettingsRepository jmapSettingsRepository;
    private DomainBasedSignatureTextFactory testee;

    @BeforeEach
    void setUp() {
        repository = new MemoryDomainSignatureTemplateRepository();
        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        testee = new DomainBasedSignatureTextFactory(repository, jmapSettingsRepository);
    }

    @Test
    void forUserShouldReturnEmptyWhenNoDomainTemplate() {
        assertThat(testee.forUser(BOB).block()).isEmpty();
    }

    @Test
    void forUserShouldReturnEmptyWhenUserHasNoDomain() {
        repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(Locale.ENGLISH, EN_SIG))).block();

        assertThat(testee.forUser(BOB_NO_DOMAIN).block()).isEmpty();
    }

    @Test
    void forUserShouldReturnDefaultLanguageTemplateWhenUserHasNoLanguageSetting() {
        repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, EN_SIG,
            Locale.FRENCH, FR_SIG))).block();

        assertThat(testee.forUser(BOB).block()).contains(EN_SIG);
    }

    @Test
    void forUserShouldReturnUserLanguageTemplate() {
        repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, EN_SIG,
            Locale.FRENCH, FR_SIG))).block();

        Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "fr"))).block();

        assertThat(testee.forUser(BOB).block()).contains(FR_SIG);
    }

    @Test
    void forUserShouldFallBackToEnglishWhenUserLanguageNotInTemplate() {
        repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, EN_SIG))).block();

        Mono.from(jmapSettingsRepository.updatePartial(BOB, JmapSettingsPatch$.MODULE$.toUpsert(LANGUAGE_KEY, "fr"))).block();

        assertThat(testee.forUser(BOB).block()).contains(EN_SIG);
    }

    @Test
    void forUserShouldReturnEmptyWhenDomainTemplateHasNoEnglishFallback() {
        repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
            Locale.FRENCH, FR_SIG))).block();

        assertThat(testee.forUser(BOB).block()).isEmpty();
    }

    @Test
    void ldapPlaceholdersShouldBeInterpolatedByCallerAfterForUser() {
        SignatureText templateWithPlaceholders = new SignatureText(
            "Regards, {ldap:givenName} {ldap:sn}",
            "<p>Regards, {ldap:givenName} {ldap:sn}</p>");
        repository.store(DOMAIN, new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, templateWithPlaceholders))).block();

        SignatureText raw = testee.forUser(BOB).block().get();
        SignatureText interpolated = raw.interpolate(Map.of("givenName", "John", "sn", "Doe"));

        assertThat(interpolated.textSignature()).isEqualTo("Regards, John Doe");
        assertThat(interpolated.htmlSignature()).isEqualTo("<p>Regards, John Doe</p>");
    }
}
