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

package com.linagora.tmail.encrypted.postgres.table;

import static com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule.PostgresEncryptedEmailStoreTable.TABLE;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;
import org.jooq.postgres.extensions.bindings.HstoreBinding;
import org.jooq.postgres.extensions.types.Hstore;

public interface PostgresEncryptedEmailStoreModule {
    interface PostgresEncryptedEmailStoreTable {
        Table<Record> TABLE_NAME = DSL.table("encrypted_email_content");

        Field<UUID> MESSAGE_ID = DSL.field("message_id", SQLDataType.UUID);
        Field<String> ENCRYPTED_PREVIEW = DSL.field("encrypted_preview", SQLDataType.VARCHAR.notNull());
        Field<String> ENCRYPTED_HTML = DSL.field("encrypted_html", SQLDataType.VARCHAR.notNull());
        Field<Boolean> HAS_ATTACHMENT = DSL.field("has_attachment", SQLDataType.BOOLEAN.notNull());
        Field<String> ENCRYPTED_ATTACHMENT_METADATA = DSL.field("encrypted_attachment_metadata", SQLDataType.VARCHAR);
        Field<Hstore> POSITION_BLOB_ID_MAPPING = DSL.field("position_blob_id_mapping", DefaultDataType.getDefaultDataType("hstore").asConvertedDataType(new HstoreBinding()).notNull());

        Name PK_CONSTRAINT_NAME = DSL.name("encrypted_email_content_pkey");

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(MESSAGE_ID)
                .column(ENCRYPTED_PREVIEW)
                .column(ENCRYPTED_HTML)
                .column(HAS_ATTACHMENT)
                .column(ENCRYPTED_ATTACHMENT_METADATA)
                .column(POSITION_BLOB_ID_MAPPING)
                .constraint(DSL.constraint(PK_CONSTRAINT_NAME).primaryKey(MESSAGE_ID))))
            .disableRowLevelSecurity()
            .build();
    }

    PostgresDataDefinition MODULE = PostgresDataDefinition.builder()
        .addTable(TABLE)
        .build();
}
