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

package com.linagora.tmail.webadmin.domainsignature;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linagora.tmail.james.jmap.domainsignature.ApplyResult;

public record ApplyResultDTO(
    @JsonProperty("applied") int applied,
    @JsonProperty("skipped") int skipped,
    @JsonProperty("error") int error) {

    public static ApplyResultDTO from(ApplyResult result) {
        return new ApplyResultDTO(result.applied(), result.skipped(), result.error());
    }
}
