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

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;

import com.google.common.base.Preconditions;

import ezvcard.VCard;
import ezvcard.parameter.EmailType;
import ezvcard.property.StructuredName;
import ezvcard.property.Uid;

public record CardDavCreationObjectRequest(String version,
                                           String uid,
                                           Optional<String> fullName,
                                           Optional<List<String>> nameList,
                                           Email email) {

    public CardDavCreationObjectRequest {
        Preconditions.checkArgument(StringUtils.isNotEmpty(version), "Version should not be empty");
        Preconditions.checkArgument(StringUtils.isNotEmpty(uid), "Uid should not be empty");
        Preconditions.checkArgument(email != null, "Email should not be null");
    }

    public record Email(List<EmailType> type,
                        MailAddress value) {

        public Email {
            Preconditions.checkArgument(!type.isEmpty(), "Email type should not be empty");
            Preconditions.checkArgument(value != null, "Email value should not be null");
        }
    }

    /**
     * @see <a href="https://tools.ietf.org/html/rfc6350">RFC 6350</a>
     */
    public String toVCard() {
        VCard vCard = new VCard();

        vCard.setVersion(ezvcard.VCardVersion.V4_0);

        fullName.ifPresent(vCard::setFormattedName);

        nameList.ifPresent(names -> {
            StructuredName structuredName = new StructuredName();
            structuredName.setFamily(!names.isEmpty() ? names.get(0) : StringUtils.EMPTY);
            structuredName.setGiven(names.size() > 1 ? names.get(1) : StringUtils.EMPTY);
            if (names.size() > 2) {
                structuredName.getAdditionalNames().add(names.get(2));
            }
            vCard.setStructuredName(structuredName);
        });

        vCard.setUid(new Uid(uid));
        vCard.addEmail(email.value().asString(), email.type().toArray(new EmailType[0]));
        return ezvcard.Ezvcard.write(vCard)
            .prodId(false)
            .go();
    }

}
