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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;

public class OperationLimitationsDTO {
    private final List<RateLimitationDTO> rateLimitationDTOList;

    @JsonCreator
    public OperationLimitationsDTO(List<RateLimitationDTO> rateLimitationDTOList) {
        Preconditions.checkArgument(!rateLimitationDTOList.isEmpty(), "Operation limitation arrays must have at least one entry.");
        this.rateLimitationDTOList = rateLimitationDTOList;
    }

    @JsonValue
    public List<RateLimitationDTO> getRateLimitationDTOList() {
        return rateLimitationDTOList;
    }
}
