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
 ********************************************************************/

package com.linagora.tmail.james.jmap.firebase;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.james.backends.postgres.PostgresCommons;
import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresFirebaseModule {

    interface FirebaseSubscriptionTable {
        Table<Record> TABLE_NAME = DSL.table("firebase_subscription");
        String PRIMARY_KEY_CONSTRAINT = "firebase_subscription_primary_key_constraint";
        String FCM_TOKEN_UNIQUE_CONSTRAINT = "fcm_token_unique_constraint";

        Field<String> USER = DSL.field("username", SQLDataType.VARCHAR.notNull());
        Field<String> DEVICE_CLIENT_ID = DSL.field("device_client_id", SQLDataType.VARCHAR.notNull());
        Field<UUID> ID = DSL.field("id", SQLDataType.UUID.notNull());
        Field<OffsetDateTime> EXPIRES = DSL.field("expires", SQLDataType.TIMESTAMPWITHTIMEZONE(6));
        Field<String[]> TYPES = DSL.field("types", PostgresCommons.DataTypes.STRING_ARRAY.notNull());
        Field<String> FCM_TOKEN = DSL.field("fcm_token", SQLDataType.VARCHAR.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USER)
                .column(DEVICE_CLIENT_ID)
                .column(ID)
                .column(EXPIRES)
                .column(TYPES)
                .column(FCM_TOKEN)
                .constraint(DSL.constraint(PRIMARY_KEY_CONSTRAINT)
                    .primaryKey(USER, DEVICE_CLIENT_ID))
                .constraint(DSL.constraint(FCM_TOKEN_UNIQUE_CONSTRAINT)
                    .unique(FCM_TOKEN))
                .comment("Hold user firebase push subscriptions data")))
            .supportsRowLevelSecurity()
            .build();


        PostgresIndex USERNAME_INDEX = PostgresIndex.name("firebase_subscription_username_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USER));
        PostgresIndex USERNAME_ID_INDEX = PostgresIndex.name("firebase_subscription_username_id_index")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USER, ID));
    }

    PostgresDataDefinition MODULE = PostgresDataDefinition.builder()
        .addTable(FirebaseSubscriptionTable.TABLE)
        .addIndex(FirebaseSubscriptionTable.USERNAME_INDEX, FirebaseSubscriptionTable.USERNAME_ID_INDEX)
        .build();
}
