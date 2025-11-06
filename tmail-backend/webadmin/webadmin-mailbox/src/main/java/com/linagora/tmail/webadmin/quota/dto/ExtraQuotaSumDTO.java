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

package com.linagora.tmail.webadmin.quota.dto;

import static com.linagora.tmail.webadmin.quota.dto.UserSpecificQuotaDTO.asLong;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.mailbox.quota.model.ExtraQuotaSum;

public record ExtraQuotaSumDTO(
    Long extraStorageLimit,
    Long extraCountLimit) {

    @JsonCreator
    public ExtraQuotaSumDTO(@JsonProperty("storageLimit") Long extraStorageLimit,
                            @JsonProperty("countLimit") Long extraCountLimit) {
        this.extraStorageLimit = extraStorageLimit;
        this.extraCountLimit = extraCountLimit;
    }

    public static ExtraQuotaSumDTO from(ExtraQuotaSum extraQuotaSum) {
        return new ExtraQuotaSumDTO(
            asLong(extraQuotaSum.storageLimit()),
            asLong(extraQuotaSum.countLimit()));
    }
}
