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

package com.linagora.tmail.webadmin.mailinglist;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;

/**
 * Repository used when no mailing list configuration is available. Every operation fails, so the routes answer with a
 * {@code 409 Conflict}.
 */
public class UnconfiguredMailingListRepository implements MailingListRepository {
    @Override
    public List<MailAddress> list() {
        throw new MailingListsNotConfiguredException();
    }

    @Override
    public List<MailAddress> list(Domain domain) {
        throw new MailingListsNotConfiguredException();
    }

    @Override
    public Optional<MailingList> get(MailAddress address) {
        throw new MailingListsNotConfiguredException();
    }
}
