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

import java.util.HashMap;
import java.util.Optional;

public class RateLimitingPlanCreateRequestDTO extends HashMap<String, Optional<OperationLimitationsDTO>> {
    public static final String TRANSIT_LIMIT_KEY = "transitLimits";
    public static final String DELIVERY_LIMIT_KEY = "deliveryLimits";
    public static final String RELAY_LIMIT_KEY = "relayLimits";
}
