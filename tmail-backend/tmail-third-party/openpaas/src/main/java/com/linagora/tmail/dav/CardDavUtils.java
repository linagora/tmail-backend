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

package com.linagora.tmail.dav;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.MailAddress;

import com.google.common.hash.Hashing;
import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;

import ezvcard.parameter.EmailType;

public class CardDavUtils {
    private static final String VERSION = "4.0";
    private static final EmailType DEFAULT_EMAIL_TYPE = EmailType.WORK;

    public static CardDavCreationObjectRequest createObjectCreationRequest(Optional<String> maybeFullName, MailAddress email) {
        CardDavCreationObjectRequest.Email emailObject = new CardDavCreationObjectRequest.Email(List.of(DEFAULT_EMAIL_TYPE), email);
        return new CardDavCreationObjectRequest(VERSION, createContactUid(email), maybeFullName, Optional.empty(), emailObject);
    }

    public static CardDavCreationObjectRequest createObjectCreationRequest(MailAddress email) {
        return createObjectCreationRequest(Optional.empty(), email);
    }

    public static String createContactUid(MailAddress email) {
        return Hashing.sha1()
            .hashString(email.asString(), StandardCharsets.UTF_8)
            .toString();
    }
}
