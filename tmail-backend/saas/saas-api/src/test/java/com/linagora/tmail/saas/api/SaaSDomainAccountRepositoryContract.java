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

package com.linagora.tmail.saas.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Domain;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public interface SaaSDomainAccountRepositoryContract {

    SaaSAccount SAAS_ACCOUNT = new SaaSAccount(false, true);
    SaaSAccount SAAS_ACCOUNT_2 = new SaaSAccount(true, true);

    Domain DOMAIN = Domain.of("example.com");
    Domain OTHER_DOMAIN = Domain.of("other.com");

    SaaSDomainAccountRepository testee();

    @Test
    default void upsertSaasDomainAccountShouldSucceed() {
        Mono.from(testee().upsertSaasDomainAccount(DOMAIN, SAAS_ACCOUNT)).block();

        assertThat(Mono.from(testee().getSaaSDomainAccount(DOMAIN)).block())
            .isEqualTo(SAAS_ACCOUNT);
    }

    @Test
    default void getSaaSDomainAccountShouldReturnEmptyWhenNotFound() {
        assertThat(Mono.from(testee().getSaaSDomainAccount(DOMAIN)).block())
            .isNull();
    }

    @Test
    default void upsertSaasDomainAccountShouldOverridePreviousValue() {
        Mono.from(testee().upsertSaasDomainAccount(DOMAIN, SAAS_ACCOUNT)).block();
        Mono.from(testee().upsertSaasDomainAccount(DOMAIN, SAAS_ACCOUNT_2)).block();

        assertThat(Mono.from(testee().getSaaSDomainAccount(DOMAIN)).block())
            .isEqualTo(SAAS_ACCOUNT_2);
    }

    @Test
    default void deleteSaaSDomainAccountShouldSucceed() {
        Mono.from(testee().upsertSaasDomainAccount(DOMAIN, SAAS_ACCOUNT)).block();
        Mono.from(testee().deleteSaaSDomainAccount(DOMAIN)).block();

        assertThat(Mono.from(testee().getSaaSDomainAccount(DOMAIN)).block())
            .isNull();
    }

    @Test
    default void deleteSaaSDomainAccountShouldBeIdempotent() {
        Mono.from(testee().upsertSaasDomainAccount(DOMAIN, SAAS_ACCOUNT)).block();

        Mono.from(testee().deleteSaaSDomainAccount(DOMAIN)).block();
        Mono.from(testee().deleteSaaSDomainAccount(DOMAIN)).block();

        assertThat(Mono.from(testee().getSaaSDomainAccount(DOMAIN)).block())
            .isNull();
    }

    @Test
    default void upsertShouldNotAffectOtherDomains() {
        Mono.from(testee().upsertSaasDomainAccount(DOMAIN, SAAS_ACCOUNT)).block();

        assertThat(Mono.from(testee().getSaaSDomainAccount(OTHER_DOMAIN)).block())
            .isNull();
    }

    @Test
    default void setSaaSDomainAccountAfterDeleteShouldSucceed() {
        Mono.from(testee().upsertSaasDomainAccount(DOMAIN, SAAS_ACCOUNT)).block();
        Mono.from(testee().deleteSaaSDomainAccount(DOMAIN)).block();

        Mono.from(testee().upsertSaasDomainAccount(DOMAIN, SAAS_ACCOUNT_2)).block();

        assertThat(Mono.from(testee().getSaaSDomainAccount(DOMAIN)).block())
            .isEqualTo(SAAS_ACCOUNT_2);
    }
}
