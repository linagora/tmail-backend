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

package com.linagora.tmail.saas.model;

import java.util.stream.Stream;

import com.google.common.base.Preconditions;

public enum SaaSPlan {
    FREE("free"),
    STANDARD("standard"),
    PREMIUM("premium");

    public static SaaSPlan from(String rawValue) {
        Preconditions.checkNotNull(rawValue);

        return Stream.of(values())
            .filter(plan -> plan.value.equalsIgnoreCase(rawValue))
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException(String.format("Invalid SaaS plan '%s'", rawValue)));
    }

    private final String value;

    SaaSPlan(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
