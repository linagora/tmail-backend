/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

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
            switch (scope) {
                case Global:
                    return global(value);
                case Domain:
                    return domain(value);
                case User:
                    return teamMailbox(value);
            }
            return this;
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
