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

package com.linagora.tmail.webadmin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RateLimitationDTO(String name,
                                Long periodInSeconds,
                                Long count,
                                Long size) {
    @JsonCreator
    public RateLimitationDTO(@JsonProperty(value = "name", required = true) String name,
                             @JsonProperty(value = "periodInSeconds", required = true) Long periodInSeconds,
                             @JsonProperty(value = "count", required = true) Long count,
                             @JsonProperty(value = "size", required = true) Long size) {
        this.name = name;
        this.periodInSeconds = periodInSeconds;
        this.count = count;
        this.size = size;
    }
}
