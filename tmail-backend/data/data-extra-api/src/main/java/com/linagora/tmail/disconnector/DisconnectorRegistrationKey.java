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

package com.linagora.tmail.disconnector;

import java.util.Objects;

import org.apache.james.events.RegistrationKey;

public record DisconnectorRegistrationKey() implements RegistrationKey {
    public static class Factory implements RegistrationKey.Factory {
        @Override
        public Class<? extends RegistrationKey> forClass() {
            return DisconnectorRegistrationKey.class;
        }

        @Override
        public RegistrationKey fromString(String asString) {
            if (!Objects.equals(VALUE, asString)) {
                throw new IllegalArgumentException("Invalid DisconnectorRegistrationKey: " + asString);
            }
            return KEY;
        }
    }

    public static final DisconnectorRegistrationKey KEY = new DisconnectorRegistrationKey();
    public static final String VALUE = "tmail-disconnector";

    @Override
    public String asString() {
        return VALUE;
    }
}
