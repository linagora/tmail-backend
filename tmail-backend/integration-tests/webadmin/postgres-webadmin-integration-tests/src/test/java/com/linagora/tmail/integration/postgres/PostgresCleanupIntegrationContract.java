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

package com.linagora.tmail.integration.postgres;

import static com.linagora.tmail.integration.postgres.PostgresWebAdminBase.JAMES_SERVER_EXTENSION_SUPPLIER;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.integration.CleanupIntegrationContract;

public class PostgresCleanupIntegrationContract extends CleanupIntegrationContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_SUPPLIER.get().build();
}
