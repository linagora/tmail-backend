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

package com.linagora.tmail;

import org.apache.james.user.lib.UsersDAO;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.linagora.tmail.combined.identity.CombinedUserDAO;

public class DatabaseCombinedUserRequireModule<T extends UsersDAO> extends AbstractModule {
    public static DatabaseCombinedUserRequireModule<?> of(Class<? extends UsersDAO> typeUserDAOClass) {
        return new DatabaseCombinedUserRequireModule<>(typeUserDAOClass);
    }

    final Class<T> typeUserDAOClass;

    public DatabaseCombinedUserRequireModule(Class<T> typeUserDAOClass) {
        this.typeUserDAOClass = typeUserDAOClass;
    }

    @Provides
    @Singleton
    @Named(CombinedUserDAO.DATABASE_INJECT_NAME)
    public UsersDAO provideDatabaseCombinedUserDAO(Injector injector) {
        return injector.getInstance(typeUserDAOClass);
    }

}
