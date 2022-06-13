package com.linagora.tmail.james.jmap.ticket

import java.net.InetAddress

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.ticket.CassandraTicketStore.{clientAddress, generatedOn, key, ticketTable, username, validUntil}
import javax.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.init.{CassandraTypesProvider, CassandraZonedDateTimeModule}
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate
import reactor.core.scala.publisher.SMono

case class CassandraTicketStoreModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraTicketStore]).in(Scopes.SINGLETON)
    bind(classOf[TicketStore]).to(classOf[CassandraTicketStore])

    Multibinder.newSetBinder(binder, classOf[CassandraModule])
      .addBinding().toInstance(CassandraTicketStore.module)
  }
}

object CassandraTicketStore {
  val ticketTable = "ticket"
  val key: String = "key"
  val clientAddress: String = "clientAddress"
  val generatedOn: String = "generatedOn"
  val validUntil: String = "validUntil"
  val username: String = "username"

  val module: CassandraModule = CassandraModule.table(ticketTable)
    .comment("Secondary, short lived, single use, authentication system for securing WebSocket JMAP access")
    .options(options => options
      .withCaching(true, SchemaBuilder.RowsPerPartition.NONE))
    .statement(statement => types => statement
      .withPartitionKey(key, DataTypes.UUID)
      .withColumn(clientAddress, DataTypes.TEXT)
      .withColumn(username, DataTypes.TEXT)
      .withColumn(generatedOn, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME).copy(true))
      .withColumn(validUntil, types.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME).copy(true)))
    .build
}

class CassandraTicketStore @Inject() (val session: CqlSession, val typesProvider: CassandraTypesProvider) extends TicketStore {
  private val insert = session.prepare(insertInto(ticketTable)
    .value(key, bindMarker(key))
    .value(clientAddress, bindMarker(clientAddress))
    .value(generatedOn, bindMarker(generatedOn))
    .value(validUntil, bindMarker(validUntil))
    .value(username, bindMarker(username))
    .usingTtl(120)
    .build())

  private val delete = session.prepare(deleteFrom(ticketTable)
    .whereColumn(key).isEqualTo(bindMarker(key))
    .build())

  private val select = session.prepare(selectFrom(ticketTable)
    .all()
    .whereColumn(key).isEqualTo(bindMarker(key))
    .build())

  private val executor = new CassandraAsyncExecutor(session)
  private val dateType = typesProvider.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME)

  override def persist(ticket: Ticket): SMono[Unit] =
    SMono(executor.executeVoid(
      insert.bind()
        .setUuid(key, ticket.value.value)
        .setString(clientAddress, ticket.clientAddress.getHostAddress)
        .setString(username, ticket.username.asString)
        .setUuid(key, ticket.value.value)
        .setUdtValue(generatedOn, CassandraZonedDateTimeModule.toUDT(dateType, ticket.generatedOn.date))
        .setUdtValue(validUntil, CassandraZonedDateTimeModule.toUDT(dateType, ticket.validUntil.date))))
      .`then`

  override def retrieve(value: TicketValue): SMono[Ticket] =
    SMono(executor.executeSingleRow(
      select.bind()
        .setUuid(key, value.value)))
      .map(row => Ticket(
        clientAddress = InetAddress.getByName(row.getString(clientAddress)),
        value = TicketValue(row.getUuid(key)),
        generatedOn = UTCDate(CassandraZonedDateTimeModule.fromUDT(row.getUdtValue(generatedOn))),
        validUntil = UTCDate(CassandraZonedDateTimeModule.fromUDT(row.getUdtValue(validUntil))),
        username = Username.of(row.getString(username))))

  override def delete(ticketValue: TicketValue): SMono[Unit] =
    SMono(executor.executeVoid(
      delete.bind()
        .setUuid(key, ticketValue.value)))
      .`then`
}