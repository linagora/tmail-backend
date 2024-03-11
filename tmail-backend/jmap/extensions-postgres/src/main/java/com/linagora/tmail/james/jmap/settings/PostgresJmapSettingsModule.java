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

package com.linagora.tmail.james.jmap.settings;

import static com.linagora.tmail.james.jmap.settings.PostgresJmapSettingsModule.PostgresJmapSettingsTable.TABLE;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;
import org.jooq.postgres.extensions.bindings.HstoreBinding;
import org.jooq.postgres.extensions.types.Hstore;

public interface PostgresJmapSettingsModule {
    interface PostgresJmapSettingsTable {
        Table<Record> TABLE_NAME = DSL.table("jmap_settings");

        Field<String> USER = DSL.field("username", SQLDataType.VARCHAR.notNull());
        Field<UUID> STATE = DSL.field("state", SQLDataType.UUID.notNull());
        Field<Hstore> SETTINGS = DSL.field("settings", DefaultDataType.getDefaultDataType("hstore").asConvertedDataType(new HstoreBinding()).notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USER)
                .column(STATE)
                .column(SETTINGS)
                .primaryKey(USER)))
            .supportsRowLevelSecurity()
            .build();
    }

    PostgresModule MODULE = PostgresModule.builder()
        .addTable(TABLE)
        .build();
}
