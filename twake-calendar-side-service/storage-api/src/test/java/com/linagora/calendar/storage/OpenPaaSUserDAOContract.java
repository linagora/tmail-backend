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

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

public interface OpenPaaSUserDAOContract {

    Username USERNAME = Username.of("user@domain.tld");
    Username USERNAME_2 = Username.of("username@domain.other.tld");


    OpenPaaSUserDAO testee();

    @Test
    default void listShouldReturnEmptyByDefault() {
        List<OpenPaaSUser> actual = testee().list().collectList().block();
        assertThat(actual).isEmpty();
    }

    @Test
    default void listShouldReturnAddedResults() {
        OpenPaaSUser actual = testee().add(USERNAME).block();

        assertThat(testee().list().collectList().block()).containsOnly(actual);
    }

    @Test
    default void retriveByUsernameShouldReturnAddedResult() {
        OpenPaaSUser actual = testee().add(USERNAME).block();

        assertThat(testee().retrieve(USERNAME).block()).isEqualTo(actual);
    }

    @Test
    default void retriveByIdShouldReturnAddedResult() {
        OpenPaaSUser actual = testee().add(USERNAME).block();

        assertThat(testee().retrieve(actual.id()).block()).isEqualTo(actual);
    }

    @Test
    default void retriveByUsernameShouldReturnEmptyByDefault() {
        assertThat(testee().retrieve(USERNAME).blockOptional()).isEmpty();
    }

    @Test
    default void retriveByIdShouldReturnEmptyByDefault() {
        assertThat(testee().retrieve(new OpenPaaSId("659387b9d486dc0046aeff21")).blockOptional()).isEmpty();
    }

    @Test
    default void addShouldThrowWhenCalledTwice() {
        testee().add(USERNAME).block();

        assertThatThrownBy(() -> testee().add(USERNAME).block())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    default void listShouldBeMultiValued() {
        testee().add(USERNAME).block();
        testee().add(USERNAME_2).block();

        assertThat(testee().list().map(OpenPaaSUser::username).collectList().block()).containsOnly(USERNAME, USERNAME_2);
    }
}
