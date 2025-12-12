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

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

public interface SaaSDomainSubscriptionMessage {
    record SaaSDomainValidSubscriptionMessage(String domain, Optional<Boolean> mailDnsConfigurationValidated, SaasFeatures features) implements SaaSDomainSubscriptionMessage {

        @JsonCreator
        public SaaSDomainValidSubscriptionMessage(@JsonProperty("domain") String domain,
                                                  @JsonProperty("mailDnsConfigurationValidated") Optional<Boolean> mailDnsConfigurationValidated,
                                                  @JsonProperty("features") SaasFeatures features) {
            Preconditions.checkNotNull(domain, "domain cannot be null");
            Preconditions.checkNotNull(mailDnsConfigurationValidated, "mailDnsConfigurationValidated cannot be null");
            Preconditions.checkNotNull(features, "features cannot be null");

            this.domain = domain;
            this.mailDnsConfigurationValidated = mailDnsConfigurationValidated;
            this.features = features;
        }
    }

    record SaaSDomainCancelSubscriptionMessage(@JsonProperty("domain") String domain,
                                               @JsonProperty("enabled") Boolean enabled) implements SaaSDomainSubscriptionMessage {
        @JsonCreator
        public SaaSDomainCancelSubscriptionMessage {
            Preconditions.checkNotNull(domain, "domain cannot be null");
            Preconditions.checkNotNull(enabled, "enabled cannot be null");
        }
    }
}

