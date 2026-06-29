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

import java.util.Collection;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.linagora.tmail.migration.core.MigratedUsersRepository;

import reactor.core.publisher.Flux;

/**
 * Matches the recipients of a mail that are registered as migrated (so they must be routed to the new
 * backend). Used in {@code mailetcontainer.xml} to drive the recipient-based SMTP routing.
 */
public class RecipientIsMigrated extends GenericMatcher {
    private final MigratedUsersRepository migratedUsersRepository;

    @Inject
    public RecipientIsMigrated(MigratedUsersRepository migratedUsersRepository) {
        this.migratedUsersRepository = migratedUsersRepository;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return Flux.fromIterable(mail.getRecipients())
            .filterWhen(recipient -> migratedUsersRepository.isMigrated(Username.fromMailAddress(recipient)))
            .collectList()
            .block();
    }
}
