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

package com.linagora.tmail.james.jmap.projections.table;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresKeywordEmailQueryViewTable {
    Table<Record> TABLE_NAME = DSL.table("keyword_view");

    Field<String> USERNAME = DSL.field("username", SQLDataType.VARCHAR(255).notNull());
    Field<String> KEYWORD = DSL.field("keyword", SQLDataType.VARCHAR.notNull());
    Field<OffsetDateTime> RECEIVED_AT = DSL.field("received_at", SQLDataType.TIMESTAMPWITHTIMEZONE.notNull());
    Field<UUID> MESSAGE_ID = DSL.field("message_id", SQLDataType.UUID.notNull());
    Field<UUID> THREAD_ID = DSL.field("thread_id", SQLDataType.UUID);

    Name KEYWORD_VIEW_PK_CONSTRAINT_NAME = DSL.name("keyword_view_pkey");

    PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
        .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
            .column(USERNAME)
            .column(KEYWORD)
            .column(MESSAGE_ID)
            .column(RECEIVED_AT)
            .column(THREAD_ID)
            .constraint(DSL.constraint(KEYWORD_VIEW_PK_CONSTRAINT_NAME).primaryKey(USERNAME, KEYWORD, MESSAGE_ID))))
        .supportsRowLevelSecurity()
        .build();

    PostgresIndex USERNAME_KEYWORD_RECEIVED_AT_INDEX = PostgresIndex.name("email_query_view_username_keyword_received_at_index")
        .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
            .on(TABLE_NAME, USERNAME, KEYWORD, RECEIVED_AT));
}
