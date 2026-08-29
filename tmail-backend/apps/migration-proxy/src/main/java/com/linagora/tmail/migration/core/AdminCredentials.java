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

package com.linagora.tmail.migration.core;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * The backend administrator the proxy authenticates as when it needs to open a session on behalf of a
 * user whose password it does not know - which is the case for every Kerberos authenticated client.
 *
 * <p>The proxy then performs a SASL PLAIN "delegation": it authenticates as this administrator and
 * asks the backend to authorize it as the end user.
 */
public record AdminCredentials(String username, String password) {
    public AdminCredentials {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(username), "Admin username should not be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(password), "Admin password should not be empty");
    }

    /**
     * Deliberately omits the password: backends (and thus their credentials) end up in logs.
     */
    @Override
    public String toString() {
        return "AdminCredentials{username=" + username + ", password=********}";
    }
}
