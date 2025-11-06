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

import java.util.Optional;

import org.apache.james.core.quota.QuotaLimitValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.mailbox.quota.model.UserWithSpecificQuota;

public record UserSpecificQuotaDTO(
    String username,
    Optional<Long> storageLimit,
    Optional<Long> countLimit) {
    private static final long UNLIMITED = -1;

    @JsonCreator
    public UserSpecificQuotaDTO(@JsonProperty("user") String username,
                                @JsonProperty("storageLimit") Optional<Long> storageLimit,
                                @JsonProperty("countLimit") Optional<Long> countLimit) {
        this.username = username;
        this.storageLimit = storageLimit;
        this.countLimit = countLimit;
    }

    public static UserSpecificQuotaDTO from(UserWithSpecificQuota userQuota) {
        return new UserSpecificQuotaDTO(
            userQuota.username().asString(),
            userQuota.limits().sizeLimit().map(UserSpecificQuotaDTO::asLong),
            userQuota.limits().countLimit().map(UserSpecificQuotaDTO::asLong));
    }

    public static <T extends QuotaLimitValue<T>> Long asLong(QuotaLimitValue<T> limit) {
        if (limit.isUnlimited()) {
            return UNLIMITED;
        }
        return limit.asLong();
    }
}
