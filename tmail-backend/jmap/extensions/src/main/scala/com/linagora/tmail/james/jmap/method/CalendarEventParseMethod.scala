package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.james.jmap.json.CalendarEventSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{CalendarEventParseRequest, CalendarEventParseResponse, CalendarEventParseResults, CalendarEventParsed, InvalidCalendarFileException}
import eu.timepit.refined.auto._
import net.fortuna.ical4j.data.{CalendarBuilder, CalendarParserFactory, ContentHandlerContext}
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, Invocation, SessionTranslator, UrlPrefixes}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.{BlobId, BlobUnParsableException}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject
import scala.util.Using

case object CalendarCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = CalendarCapability

  override def id(): CapabilityIdentifier = LINAGORA_CALENDAR
}

case object CalendarCapability extends Capability {
  val properties: CapabilityProperties = CalendarCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_CALENDAR
}

case object CalendarCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

class CalendarCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = CalendarCapabilityFactory
}

class CalendarEventMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new CalendarCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CalendarEventParseMethod])
  }
}

class CalendarEventParseMethod @Inject()(val blobResolvers: BlobResolvers,
                                         val metricFactory: MetricFactory,
                                         val sessionTranslator: SessionTranslator,
                                         val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[CalendarEventParseRequest] {

  override val methodName: Invocation.MethodName = MethodName("CalendarEvent/parse")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_CALENDAR)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, CalendarEventParseRequest] =
    CalendarEventSerializer.deserializeCalendarEventParseRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)
      .flatMap(_.validate)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: CalendarEventParseRequest): Publisher[InvocationWithContext] =
    computeResponse(request, mailboxSession)
      .map(response => Invocation(
        methodName,
        Arguments(CalendarEventSerializer.serializeCalendarEventResponse(response).as[JsObject]),
        invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  private def computeResponse(request: CalendarEventParseRequest,
                              mailboxSession: MailboxSession): SMono[CalendarEventParseResponse] = {
    val validations: Seq[Either[CalendarEventParseResults, BlobId]] = request.blobIds.value
      .map(id => BlobId.of(id)
        .toEither
        .left
        .map(_ => CalendarEventParseResults.notFound(id)))

    val parsedIds: Seq[BlobId] = validations.flatMap(_.toOption)
    val invalid: Seq[CalendarEventParseResults] = validations.map(_.left).flatMap(_.toOption)

    val parsed: SFlux[CalendarEventParseResults] = SFlux.fromIterable(parsedIds)
      .flatMap(blobId => toParseResults(blobId, mailboxSession))

    SFlux.merge(Seq(parsed, SFlux.fromIterable(invalid)))
      .reduce(CalendarEventParseResults.empty())(CalendarEventParseResults.merge)
      .map(_.asResponse(request.accountId))
  }

  private def toParseResults(blobId: BlobId, mailboxSession: MailboxSession): SMono[CalendarEventParseResults] =
    blobResolvers.resolve(blobId, mailboxSession)
      .flatMap(blob => Using(blob.content) { content =>
        CalendarEventParsed.from(getCalendarBuilder.build(content))
      }.fold(_ => SMono.error[CalendarEventParsed](InvalidCalendarFileException(blobId)), result => SMono.just(result)))
      .map(parsed => CalendarEventParseResults.parse(blobId, parsed))
      .onErrorResume {
        case e: BlobNotFoundException => SMono.just(CalendarEventParseResults.notFound(e.blobId))
        case e: BlobUnParsableException => SMono.just(CalendarEventParseResults.notParse(e.blobId))
        case _ => SMono.just(CalendarEventParseResults.notParse(blobId))
      }

  private def getCalendarBuilder: CalendarBuilder =
    new CalendarBuilder(CalendarParserFactory.getInstance.get,
      new ContentHandlerContext().withSupressInvalidProperties(true),
      TimeZoneRegistryFactory.getInstance.createRegistry)
}