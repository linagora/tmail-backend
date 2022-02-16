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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RateLimitationDTO {
    private final String name;
    private final String period;
    private final Long count;
    private final Long size;

    @JsonCreator
    public RateLimitationDTO(@JsonProperty(value = "name", required = true) String name,
                             @JsonProperty(value = "period", required = true) String period,
                             @JsonProperty(value = "count", required = true) Long count,
                             @JsonProperty(value = "size", required = true) Long size) {
        this.name = name;
        this.period = period;
        this.count = count;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public String getPeriod() {
        return period;
    }

    public Long getCount() {
        return count;
    }

    public Long getSize() {
        return size;
    }
}
