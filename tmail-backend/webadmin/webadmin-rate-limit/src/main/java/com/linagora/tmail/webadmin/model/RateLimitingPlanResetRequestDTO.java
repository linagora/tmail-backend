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

package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RateLimitingPlanResetRequestDTO {
    private final String planName;
    private final Optional<OperationLimitationsDTO> transitLimits;
    private final Optional<OperationLimitationsDTO> relayLimits;
    private final Optional<OperationLimitationsDTO> deliveryLimits;

    @JsonCreator
    public RateLimitingPlanResetRequestDTO(@JsonProperty(value = "planName", required = true) String planName,
                                           @JsonProperty("transitLimits") Optional<OperationLimitationsDTO> transitLimitationsDTO,
                                           @JsonProperty("relayLimits") Optional<OperationLimitationsDTO> relayLimitationsDTO,
                                           @JsonProperty("deliveryLimits") Optional<OperationLimitationsDTO> deliveryLimitationsDTO) {
        this.planName = planName;
        this.transitLimits = transitLimitationsDTO;
        this.relayLimits = relayLimitationsDTO;
        this.deliveryLimits = deliveryLimitationsDTO;
    }

    public String getPlanName() {
        return planName;
    }

    public Optional<OperationLimitationsDTO> getTransitLimits() {
        return transitLimits;
    }

    public Optional<OperationLimitationsDTO> getRelayLimits() {
        return relayLimits;
    }

    public Optional<OperationLimitationsDTO> getDeliveryLimits() {
        return deliveryLimits;
    }
}
