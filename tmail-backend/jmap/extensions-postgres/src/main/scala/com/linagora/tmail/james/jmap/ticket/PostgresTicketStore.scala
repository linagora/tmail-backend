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

package com.linagora.tmail.james.jmap.ticket

import java.net.InetAddress
import java.time.{Clock, LocalDateTime, OffsetDateTime, ZonedDateTime}
import java.util.UUID

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import jakarta.inject.Inject
import org.apache.james.backends.postgres.PostgresCommons.{LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME}
import org.apache.james.backends.postgres.utils.PostgresExecutor
import org.apache.james.backends.postgres.{PostgresCommons, PostgresDataDefinition, PostgresIndex, PostgresTable}
import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate
import org.jooq.impl.{DSL, SQLDataType}
import org.jooq.{DSLContext, Field, Record, Table}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

object PostgresTicketStore {
  private val TICKET_TABLE_NAME: Table[Record] = DSL.table("ticket")

  private val KEY: Field[UUID] = DSL.field("key", SQLDataType.UUID.notNull)
  private val CLIENT_ADDRESS: Field[String] = DSL.field("client_address", SQLDataType.VARCHAR.notNull)
  private val USERNAME: Field[String] = DSL.field("username", SQLDataType.VARCHAR.notNull)
  private val GENERATED_ON: Field[LocalDateTime] = DSL.field("generated_on", PostgresCommons.DataTypes.TIMESTAMP.notNull)
  private val VALID_UNTIL: Field[LocalDateTime] = DSL.field("valid_until", PostgresCommons.DataTypes.TIMESTAMP.notNull)
  private val CREATED_DATE: Field[OffsetDateTime] = DSL.field("created_date", SQLDataType.TIMESTAMPWITHTIMEZONE.notNull)

  private val TICKET_TABLE: PostgresTable = PostgresTable.name(TICKET_TABLE_NAME.getName)
    .createTableStep((dsl, tableName) => dsl.createTableIfNotExists(tableName)
      .column(KEY)
      .column(CLIENT_ADDRESS)
      .column(USERNAME)
      .column(GENERATED_ON)
      .column(VALID_UNTIL)
      .column(CREATED_DATE)
      .constraint(DSL.primaryKey(KEY))
      .comment("Secondary, short-lived, single-use authentication for securing WebSocket JMAP access"))
    .disableRowLevelSecurity()
    .build()

  private val TTL_INDEX: PostgresIndex = PostgresIndex.name("index_ticket_store_created_date")
    .createIndexStep((dslContext: DSLContext, indexName: String) => dslContext.createIndexIfNotExists(indexName)
      .on(TICKET_TABLE_NAME, CREATED_DATE))

  val MODULE: PostgresDataDefinition = PostgresDataDefinition.builder()
    .addTable(TICKET_TABLE)
    .addIndex(TTL_INDEX)
    .build()
}

class PostgresTicketStore @Inject()(postgresExecutor: PostgresExecutor, clock: Clock) extends TicketStore {

  import PostgresTicketStore._

  override def persist(ticket: Ticket): SMono[Unit] =
    SMono(postgresExecutor.executeVoid(dslContext => Mono.from(dslContext.insertInto(TICKET_TABLE_NAME)
      .set(KEY, ticket.value.value)
      .set(CLIENT_ADDRESS, ticket.clientAddress.getHostAddress)
      .set(USERNAME, ticket.username.asString())
      .set(GENERATED_ON, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME.apply(ticket.generatedOn.date))
      .set(VALID_UNTIL, ZONED_DATE_TIME_TO_LOCAL_DATE_TIME.apply(ticket.validUntil.date))
      .set(CREATED_DATE, ZonedDateTime.now(clock).toOffsetDateTime))))
      .`then`

  override def retrieve(value: TicketValue): SMono[Ticket] =
    SMono(postgresExecutor.executeRow(dslContext => Mono.from(dslContext.selectFrom(TICKET_TABLE_NAME)
      .where(KEY.eq(value.value)))))
      .map(row => Ticket(
        clientAddress = InetAddress.getByName(row.get(CLIENT_ADDRESS)),
        value = TicketValue(row.get(KEY)),
        generatedOn = UTCDate(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(row.get(GENERATED_ON, classOf[LocalDateTime]))),
        validUntil = UTCDate(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(row.get(VALID_UNTIL, classOf[LocalDateTime]))),
        username = Username.of(row.get(USERNAME))))

  override def delete(ticketValue: TicketValue): SMono[Unit] =
    SMono(postgresExecutor.executeVoid(dslContext => Mono.from(dslContext.deleteFrom(TICKET_TABLE_NAME)
      .where(KEY.eq(ticketValue.value)))))
      .`then`
}

class PostgresTicketStoreModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[PostgresTicketStore]).in(Scopes.SINGLETON)
    bind(classOf[TicketStore]).to(classOf[PostgresTicketStore])

    Multibinder.newSetBinder(binder, classOf[PostgresDataDefinition])
      .addBinding().toInstance(PostgresTicketStore.MODULE)
  }
}