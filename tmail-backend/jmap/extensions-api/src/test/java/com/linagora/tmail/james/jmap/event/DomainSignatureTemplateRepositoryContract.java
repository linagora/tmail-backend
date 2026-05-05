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
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText;

public interface DomainSignatureTemplateRepositoryContract {

    Domain DOMAIN_A = Domain.of("a.com");
    Domain DOMAIN_B = Domain.of("b.com");

    DomainSignatureTemplateRepository testee();

    @Test
    default void getShouldReturnEmptyWhenNothingStored() {
        assertThat(testee().get(DOMAIN_A).block()).isEmpty();
    }

    @Test
    default void getShouldReturnStoredTemplate() {
        DomainSignatureTemplate template = new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, new SignatureText("text en", "html en")));

        testee().store(DOMAIN_A, template).block();

        assertThat(testee().get(DOMAIN_A).block()).contains(template);
    }

    @Test
    default void getShouldReturnLatestTemplate() {
        DomainSignatureTemplate first = new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, new SignatureText("text v1", "html v1")));
        DomainSignatureTemplate second = new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, new SignatureText("text v2", "html v2")));

        testee().store(DOMAIN_A, first).block();
        testee().store(DOMAIN_A, second).block();

        assertThat(testee().get(DOMAIN_A).block()).contains(second);
    }

    @Test
    default void getShouldNotReturnTemplateOfOtherDomain() {
        DomainSignatureTemplate template = new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, new SignatureText("text en", "html en")));

        testee().store(DOMAIN_A, template).block();

        assertThat(testee().get(DOMAIN_B).block()).isEmpty();
    }

    @Test
    default void getShouldReturnMultiLocaleTemplate() {
        DomainSignatureTemplate template = new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, new SignatureText("text en", "html en"),
            Locale.FRENCH, new SignatureText("texte fr", "html fr")));

        testee().store(DOMAIN_A, template).block();

        assertThat(testee().get(DOMAIN_A).block()).contains(template);
    }

    @Test
    default void deleteShouldRemoveTemplate() {
        DomainSignatureTemplate template = new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, new SignatureText("text en", "html en")));

        testee().store(DOMAIN_A, template).block();
        testee().delete(DOMAIN_A).block();

        assertThat(testee().get(DOMAIN_A).block()).isEmpty();
    }

    @Test
    default void deleteShouldBeIdempotent() {
        testee().delete(DOMAIN_A).block();
        assertThat(testee().get(DOMAIN_A).block()).isEmpty();
    }

    @Test
    default void deleteShouldNotAffectOtherDomains() {
        DomainSignatureTemplate template = new DomainSignatureTemplate(Map.of(
            Locale.ENGLISH, new SignatureText("text en", "html en")));

        testee().store(DOMAIN_A, template).block();
        testee().store(DOMAIN_B, template).block();
        testee().delete(DOMAIN_A).block();

        assertThat(testee().get(DOMAIN_B).block()).contains(template);
    }
}
