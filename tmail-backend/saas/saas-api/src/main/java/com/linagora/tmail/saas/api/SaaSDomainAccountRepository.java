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

import org.apache.james.core.Domain;
import org.reactivestreams.Publisher;

import com.linagora.tmail.saas.model.SaaSAccount;

public interface SaaSDomainAccountRepository {
    Publisher<SaaSAccount> getSaaSDomainAccount(Domain domain);

    Publisher<Void> upsertSaasDomainAccount(Domain domain, SaaSAccount saaSAccount);

    Publisher<Void> deleteSaaSDomainAccount(Domain domain);
}
