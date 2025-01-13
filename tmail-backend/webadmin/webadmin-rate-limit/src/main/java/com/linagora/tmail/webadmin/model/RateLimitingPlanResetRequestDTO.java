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

import static com.linagora.tmail.webadmin.model.RateLimitingPlanCreateRequestDTO.DELIVERY_LIMIT_KEY;
import static com.linagora.tmail.webadmin.model.RateLimitingPlanCreateRequestDTO.RELAY_LIMIT_KEY;
import static com.linagora.tmail.webadmin.model.RateLimitingPlanCreateRequestDTO.TRANSIT_LIMIT_KEY;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RateLimitingPlanResetRequestDTO(String planName,
                                              Optional<OperationLimitationsDTO> transitLimits,
                                              Optional<OperationLimitationsDTO> relayLimits,
                                              Optional<OperationLimitationsDTO> deliveryLimits) {
    @JsonCreator
    public RateLimitingPlanResetRequestDTO(@JsonProperty(value = "planName", required = true) String planName,
                                           @JsonProperty(TRANSIT_LIMIT_KEY) Optional<OperationLimitationsDTO> transitLimits,
                                           @JsonProperty(RELAY_LIMIT_KEY) Optional<OperationLimitationsDTO> relayLimits,
                                           @JsonProperty(DELIVERY_LIMIT_KEY) Optional<OperationLimitationsDTO> deliveryLimits) {
        this.planName = planName;
        this.transitLimits = transitLimits;
        this.relayLimits = relayLimits;
        this.deliveryLimits = deliveryLimits;
    }
}
