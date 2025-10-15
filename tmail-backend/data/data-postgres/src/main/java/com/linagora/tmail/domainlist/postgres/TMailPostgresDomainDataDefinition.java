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

package com.linagora.tmail.domainlist.postgres;

import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_RECEIVED_PER_DAYS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_RECEIVED_PER_HOURS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_RECEIVED_PER_MINUTE;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_SENT_PER_DAYS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_SENT_PER_HOURS;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.MAILS_SENT_PER_MINUTE;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresTable;
import org.apache.james.domainlist.postgres.PostgresDomainDataDefinition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;

public interface TMailPostgresDomainDataDefinition {
    interface PostgresDomainTable {
        Table<Record> TABLE_NAME = PostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME;

        Field<String> DOMAIN = PostgresDomainDataDefinition.PostgresDomainTable.DOMAIN;

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(DOMAIN)
                .column(MAILS_SENT_PER_MINUTE)
                .column(MAILS_SENT_PER_HOURS)
                .column(MAILS_SENT_PER_DAYS)
                .column(MAILS_RECEIVED_PER_MINUTE)
                .column(MAILS_RECEIVED_PER_HOURS)
                .column(MAILS_RECEIVED_PER_DAYS)
                .primaryKey(DOMAIN)))
            .disableRowLevelSecurity()
            .build();
    }

    PostgresDataDefinition MODULE = PostgresDataDefinition.builder()
        .addTable(PostgresDomainTable.TABLE)
        .build();
}
