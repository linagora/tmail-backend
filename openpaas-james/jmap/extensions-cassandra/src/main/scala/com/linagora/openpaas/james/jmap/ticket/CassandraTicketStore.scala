package com.linagora.openpaas.james.jmap.ticket

import java.net.InetAddress

import com.datastax.driver.core.DataType.{text, uuid}
import com.datastax.driver.core.Session
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder.{bindMarker, insertInto, ttl}
import com.datastax.driver.core.schemabuilder.{Create, SchemaBuilder}
import com.linagora.openpaas.james.jmap.ticket.CassandraTicketStore.{clientAddress, generatedOn, key, ticketTable, username, validUntil}
import javax.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.init.{CassandraTypesProvider, CassandraZonedDateTimeModule}
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate
import reactor.core.scala.publisher.SMono

object CassandraTicketStore {
  val ticketTable = "ticket"
  val key: String = "key"
  val clientAddress: String = "clientAddress"
  val generatedOn: String = "generatedOn"
  val validUntil: String = "validUntil"
  val username: String = "username"

  val module: CassandraModule = CassandraModule.table(ticketTable)
    .comment("Secondary, short lived, single use, authentication system for securing WebSocket JMAP access")
    .options((options: Create.Options) => options
      .caching(
        SchemaBuilder.KeyCaching.ALL,
        SchemaBuilder.noRows()))
    .statement((statement: Create) => statement
      .addPartitionKey(key, uuid)
      .addColumn(clientAddress, text)
      .addColumn(username, text)
      .addUDTColumn(generatedOn, SchemaBuilder.frozen(CassandraZonedDateTimeModule.ZONED_DATE_TIME))
      .addUDTColumn(validUntil, SchemaBuilder.frozen(CassandraZonedDateTimeModule.ZONED_DATE_TIME)))
    .build
}

class CassandraTicketStore @Inject() (val session: Session, val typesProvider: CassandraTypesProvider) extends TicketStore {
  private val insert = session.prepare(insertInto(ticketTable)
    .value(key, bindMarker(key))
    .value(clientAddress, bindMarker(clientAddress))
    .value(generatedOn, bindMarker(generatedOn))
    .value(validUntil, bindMarker(validUntil))
    .value(username, bindMarker(username))
    .using(ttl(120)))

  private val delete = session.prepare(QueryBuilder.delete().from(ticketTable)
    .where(QueryBuilder.eq(key, bindMarker(key))))

  private val select = session.prepare(QueryBuilder.select().from(ticketTable)
    .where(QueryBuilder.eq(key, bindMarker(key))))

  private val executor = new CassandraAsyncExecutor(session)
  private val dateType = typesProvider.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME)

  override def persist(ticket: Ticket): SMono[Unit] =
    SMono(executor.executeVoid(
      insert.bind()
        .setUUID(key, ticket.value.value)
        .setString(clientAddress, ticket.clientAddress.getHostAddress)
        .setString(username, ticket.username.asString)
        .setUUID(key, ticket.value.value)
        .setUDTValue(generatedOn, CassandraZonedDateTimeModule.toUDT(dateType, ticket.generatedOn.date))
        .setUDTValue(validUntil, CassandraZonedDateTimeModule.toUDT(dateType, ticket.validUntil.date))))
      .`then`

  override def retrieve(value: TicketValue): SMono[Ticket] =
    SMono(executor.executeSingleRow(
      select.bind()
        .setUUID(key, value.value)))
      .map(row => Ticket(
        clientAddress = InetAddress.getByName(row.getString(clientAddress)),
        value = TicketValue(row.getUUID(key)),
        generatedOn = UTCDate(CassandraZonedDateTimeModule.fromUDT(row.getUDTValue(generatedOn))),
        validUntil = UTCDate(CassandraZonedDateTimeModule.fromUDT(row.getUDTValue(validUntil))),
        username = Username.of(row.getString(username))))

  override def delete(ticketValue: TicketValue): SMono[Unit] =
    SMono(executor.executeVoid(
      delete.bind()
        .setUUID(key, ticketValue.value)))
      .`then`
}