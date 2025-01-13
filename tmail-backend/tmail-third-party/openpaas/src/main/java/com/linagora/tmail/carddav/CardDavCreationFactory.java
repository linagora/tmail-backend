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

package com.linagora.tmail.carddav;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;

import com.google.common.hash.Hashing;

import ezvcard.parameter.EmailType;

public class CardDavCreationFactory {
    private static final String VERSION = "4.0";
    private static final EmailType EMAIL_TYPE_DEFAULT = EmailType.WORK;

    public static CardDavCreationObjectRequest create(Optional<String> fullName, MailAddress email) {
        CardDavCreationObjectRequest.Email emailObject = new CardDavCreationObjectRequest.Email(List.of(EMAIL_TYPE_DEFAULT), email);
        return new CardDavCreationObjectRequest(VERSION, createContactUid(email), fullName, Optional.empty(), emailObject);
    }

    public static CardDavCreationObjectRequest create(MailAddress email) {
        return create(Optional.empty(), email);
    }

    public static String createContactUid(MailAddress email) {
        return Hashing.sha1()
            .hashString(email.asString(), StandardCharsets.UTF_8)
            .toString();
    }
}
