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

package com.linagora.tmail.migration.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.migration.core.MemoryMigratedUsersRepository;

class RecipientIsMigratedTest {
    private static final MailAddress MIGRATED = mailAddress("migrated@domain.tld");
    private static final MailAddress NOT_MIGRATED = mailAddress("legacy@domain.tld");

    private MemoryMigratedUsersRepository repository;
    private RecipientIsMigrated matcher;

    @BeforeEach
    void setUp() {
        repository = new MemoryMigratedUsersRepository();
        matcher = new RecipientIsMigrated(repository);
    }

    @Test
    void shouldMatchOnlyMigratedRecipients() throws Exception {
        repository.addMigratedUser(Username.fromMailAddress(MIGRATED)).block();

        FakeMail mail = FakeMail.builder().name("mail").recipients(MIGRATED, NOT_MIGRATED).build();

        assertThat(matcher.match(mail)).containsExactly(MIGRATED);
    }

    @Test
    void shouldMatchNothingWhenNoRecipientMigrated() throws Exception {
        FakeMail mail = FakeMail.builder().name("mail").recipients(NOT_MIGRATED).build();

        assertThat(matcher.match(mail)).isEmpty();
    }

    private static MailAddress mailAddress(String value) {
        try {
            return new MailAddress(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
