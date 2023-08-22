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

package com.linagora.tmail.redis;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.rate.limiter.api.RateLimiterFactory;
import org.apache.james.rate.limiter.redis.RedisHealthCheck;
import org.apache.james.rate.limiter.redis.RedisRateLimiterConfiguration;
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;

public class TMailRedisModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(TMailRedisModule.class);

    @Override
    protected void configure() {
        bind(RateLimiterFactory.class).to(RedisRateLimiterFactory.class);
        bind(RedisRateLimiterFactory.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(RedisHealthCheck.class);
    }

    @Provides
    @Singleton
    public RedisRateLimiterConfiguration redisRateLimiterConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return RedisRateLimiterConfiguration.from(propertiesProvider.getConfiguration("redis"));
        } catch (FileNotFoundException e) {
            LOGGER.info("Start TMail without Redis configuration file.");
            return RedisRateLimiterConfiguration.from("redis://localhost:6379", false);
        }
    }
}
