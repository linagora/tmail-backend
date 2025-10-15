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

package com.linagora.tmail.saas.rabbitmq.subscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

public record SaaSSubscriptionMessage(String internalEmail, Boolean isPaying, Boolean canUpgrade, SaasFeatures features) {
    @JsonCreator
    public SaaSSubscriptionMessage(@JsonProperty("internalEmail") String internalEmail,
                                   @JsonProperty("isPaying") Boolean isPaying,
                                   @JsonProperty("canUpgrade") Boolean canUpgrade,
                                   @JsonProperty("features") SaasFeatures features) {
        Preconditions.checkNotNull(internalEmail, "internalEmail cannot be null");
        Preconditions.checkNotNull(isPaying, "isPaying cannot be null");
        Preconditions.checkNotNull(canUpgrade, "planName cannot be null");
        Preconditions.checkNotNull(features, "features cannot be null");

        this.internalEmail = internalEmail;
        this.isPaying = isPaying;
        this.canUpgrade = canUpgrade;
        this.features = features;
    }
}
