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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.webadmin;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.webadmin.dto.OccupationDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;

import com.google.common.base.Preconditions;

/**
 * Copied and adapted from Apache James QuotaDetailsDTO class
 */
public class TeamMailboxQuotaDetailsDTO {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<ValidatedQuotaDTO> global;
        private Optional<ValidatedQuotaDTO> domain;
        private Optional<ValidatedQuotaDTO> teamMailbox;
        private Optional<ValidatedQuotaDTO> computed;
        private OccupationDTO occupation;

        private Builder() {
            global = Optional.empty();
            teamMailbox = Optional.empty();
            computed = Optional.empty();
        }

        public Builder global(ValidatedQuotaDTO global) {
            this.global = Optional.of(global);
            return this;
        }

        public Builder domain(ValidatedQuotaDTO domain) {
            this.domain = Optional.of(domain);
            return this;
        }

        public Builder teamMailbox(ValidatedQuotaDTO teamMailbox) {
            this.teamMailbox = Optional.of(teamMailbox);
            return this;
        }

        public Builder computed(ValidatedQuotaDTO computed) {
            this.computed = Optional.of(computed);
            return this;
        }

        public Builder occupation(Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota, Quota<QuotaCountLimit, QuotaCountUsage> countQuota) {
            this.occupation = OccupationDTO.from(sizeQuota, countQuota);
            return this;
        }

        public Builder valueForScope(Quota.Scope scope, ValidatedQuotaDTO value) {
            return switch (scope) {
                case Global -> global(value);
                case Domain -> domain(value);
                case User -> teamMailbox(value);
            };
        }

        public TeamMailboxQuotaDetailsDTO build() {
            Preconditions.checkNotNull(occupation);
            return new TeamMailboxQuotaDetailsDTO(global, domain, teamMailbox, computed, occupation);
        }
    }

    private final Optional<ValidatedQuotaDTO> global;
    private final Optional<ValidatedQuotaDTO> domain;
    private final Optional<ValidatedQuotaDTO> teamMailbox;
    private final Optional<ValidatedQuotaDTO> computed;
    private final OccupationDTO occupation;

    private TeamMailboxQuotaDetailsDTO(Optional<ValidatedQuotaDTO> global, Optional<ValidatedQuotaDTO> domain, Optional<ValidatedQuotaDTO> teamMailbox, Optional<ValidatedQuotaDTO> computed, OccupationDTO occupation) {
        this.global = global;
        this.domain = domain;
        this.teamMailbox = teamMailbox;
        this.computed = computed;
        this.occupation = occupation;
    }

    public Optional<ValidatedQuotaDTO> getGlobal() {
        return global;
    }

    public Optional<ValidatedQuotaDTO> getDomain() {
        return domain;
    }

    public Optional<ValidatedQuotaDTO> getTeamMailbox() {
        return teamMailbox;
    }

    public Optional<ValidatedQuotaDTO> getComputed() {
        return computed;
    }

    public OccupationDTO getOccupation() {
        return occupation;
    }
}
