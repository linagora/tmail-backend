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

package com.linagora.calendar.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.james.core.Domain;
import org.junit.jupiter.api.Test;

public interface OpenPaaSDomainDAOContract {

    Domain DOMAIN = Domain.of("domain.tld");
    Domain DOMAIN_2 = Domain.of("domain.other.tld");


    OpenPaaSDomainDAO testee();

    @Test
    default void listShouldReturnEmptyByDefault() {
        List<OpenPaaSDomain> actual = testee().list().collectList().block();
        assertThat(actual).isEmpty();
    }

    @Test
    default void listShouldReturnAddedResults() {
        OpenPaaSDomain actual = testee().add(DOMAIN).block();

        assertThat(testee().list().collectList().block()).containsOnly(actual);
    }

    @Test
    default void retriveByDomainShouldReturnAddedResult() {
        OpenPaaSDomain actual = testee().add(DOMAIN).block();

        assertThat(testee().retrieve(DOMAIN).block()).isEqualTo(actual);
    }

    @Test
    default void retriveByIdShouldReturnAddedResult() {
        OpenPaaSDomain actual = testee().add(DOMAIN).block();

        assertThat(testee().retrieve(actual.id()).block()).isEqualTo(actual);
    }

    @Test
    default void retriveByDomainShouldReturnEmptyByDefault() {
        assertThat(testee().retrieve(DOMAIN).blockOptional()).isEmpty();
    }

    @Test
    default void retriveByIdShouldReturnEmptyByDefault() {
        assertThat(testee().retrieve(new OpenPaaSId("abcdef")).blockOptional()).isEmpty();
    }

    @Test
    default void addShouldThrowWhenCalledTwice() {
        testee().add(DOMAIN).block();

        assertThatThrownBy(() -> testee().add(DOMAIN).block())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    default void listShouldBeMultiValued() {
        testee().add(DOMAIN).block();
        testee().add(DOMAIN_2).block();

        assertThat(testee().list().map(OpenPaaSDomain::domain).collectList().block()).containsOnly(DOMAIN, DOMAIN_2);
    }
}
