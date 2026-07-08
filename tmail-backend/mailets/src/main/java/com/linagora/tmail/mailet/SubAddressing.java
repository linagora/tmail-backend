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

package com.linagora.tmail.mailet;

import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.user.api.UsersRepository;

public class SubAddressing {
    public static Optional<String> extractDetail(MailAddress recipient) {
        String localPart = recipient.getLocalPart();
        int detailStart = localPart.indexOf(UsersRepository.LOCALPART_DETAIL_DELIMITER);
        if (detailStart < 0) {
            return Optional.empty();
        }
        return Optional.of(localPart.substring(detailStart));
    }

    public static MailAddress appendDetail(MailAddress member, String detail) {
        try {
            return new MailAddress(member.getLocalPart() + detail + "@" + member.getDomain().asString());
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }
}
