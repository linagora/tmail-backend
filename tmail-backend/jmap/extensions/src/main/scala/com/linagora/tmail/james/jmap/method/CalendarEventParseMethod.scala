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

package com.linagora.tmail.james.jmap.method

import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.json.CalendarEventSerializer
import com.linagora.tmail.james.jmap.method.CalendarCapabilityProperties.CALENDAR_EVENT_VERSION
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CALENDAR
import com.linagora.tmail.james.jmap.model.{CalendarEventParse, CalendarEventParseRequest, CalendarEventParseResponse, CalendarEventParseResults, CalendarEventParsed, InvalidCalendarFileException}
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import jakarta.inject.{Inject, Named}
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, Invocation, Properties, SessionTranslator, UrlPrefixes}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.{BlobId, BlobUnParsableException, SpecificHeaderRequest}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.{BlobNotFoundException, BlobResolvers, SessionSupplier}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.utils.{InitializationOperation, InitilizationOperationBuilder}
import org.reactivestreams.Publisher
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Using

object CalendarCapabilityProperties {
  val CALENDAR_EVENT_VERSION = 2;
}

case class CalendarCapabilityFactory(supportedLanguage: CalendarEventReplySupportedLanguage,
                                     supportFreeBusyQuery: Boolean,
                                     counterSupport: Boolean) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = CalendarCapability(CalendarCapabilityProperties(
    replySupportedLanguage = supportedLanguage.valueAsStringSet,
    supportFreeBusyQuery = supportFreeBusyQuery,
    counterSupport = counterSupport))

  override def id(): CapabilityIdentifier = LINAGORA_CALENDAR
}

final case class CalendarCapability(properties: CalendarCapabilityProperties) extends Capability {
  val identifier: CapabilityIdentifier = LINAGORA_CALENDAR
}

case class CalendarCapabilityProperties(replySupportedLanguage: Set[String],
                                        supportFreeBusyQuery: Boolean,
                                        counterSupport: Boolean) extends CapabilityProperties {
  override def jsonify(): JsObject =
    Json.obj(
      "replySupportedLanguage" -> replySupportedLanguage,
      "version" -> CALENDAR_EVENT_VERSION,
      "supportFreeBusyQuery" -> supportFreeBusyQuery,
      "counterSupport" -> counterSupport)
}

class CalendarCapabilitiesModule extends AbstractModule {

  @ProvidesIntoSet
  private def capability(supportedLanguage: CalendarEventReplySupportedLanguage,
                         @Named("calDavSupport") calDavSupport: Boolean): CapabilityFactory =
    CalendarCapabilityFactory(
      supportedLanguage = supportedLanguage,
      supportFreeBusyQuery = calDavSupport,
      counterSupport = calDavSupport)
}

class CalendarEventMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new CalendarCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CalendarEventParseMethod])
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CalendarEventAcceptMethod])
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CalendarEventRejectMethod])
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CalendarEventMaybeMethod])
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CalendarEventAttendanceGetMethod])

    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CalendarEventCounterAcceptMethod])

    bind(classOf[CalendarEventReplyMailProcessor]).in(Scopes.SINGLETON)
    bind(classOf[CalendarEventReplySupportedLanguage]).in(Scopes.SINGLETON)
    bind(classOf[CalendarEventCounterPerformer]).in(Scopes.SINGLETON)
  }

  @ProvidesIntoSet
  def initCalendarEventReplyPerformer(instance: CalendarEventReplyMailProcessor): InitializationOperation = {
    InitilizationOperationBuilder.forClass(classOf[CalendarEventReplyMailProcessor])
      .init(new org.apache.james.utils.InitilizationOperationBuilder.Init() {
        override def init(): Unit = instance.init
      })
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
    validateProperties(request)
      .fold(error => SMono.error(error),
        properties => computeResponse(request, mailboxSession)
          .map(response => Invocation(
            methodName,
            Arguments(CalendarEventSerializer.serializeCalendarEventResponse(response, properties).as[JsObject]),
            invocation.invocation.methodCallId))
          .map(InvocationWithContext(_, invocation.processingContext)))

  private def validateProperties(request: CalendarEventParseRequest): Either[IllegalArgumentException, Properties] =
    request.properties match {
      case None => Right(CalendarEventParse.defaultProperties)
      case Some(properties) =>
        val invalidProperties: Set[NonEmptyString] = properties.value
          .flatMap(property => SpecificHeaderRequest.from(property)
            .fold(
              invalidProperty => Some(invalidProperty),
              _ => None
            )) -- CalendarEventParse.allowedProperties.value

        if (invalidProperties.nonEmpty) {
          Left(new IllegalArgumentException(s"The following properties [${invalidProperties.map(p => p.value).mkString(", ")}] do not exist."))
        } else if (properties.isEmpty()) {
          Right(CalendarEventParse.defaultProperties)
        } else {
          Right(properties)
        }
    }

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
      .flatMap(blob => Using(blob.content)(CalendarEventParsed.from)
        .fold(error => SMono.error[List[CalendarEventParsed]](InvalidCalendarFileException(blobId, error)), result => SMono.just(result)))
      .map(parsed => CalendarEventParseResults.parse(blobId, parsed))
      .onErrorResume {
        case e: BlobNotFoundException => SMono.just(CalendarEventParseResults.notFound(e.blobId))
        case e: BlobUnParsableException => SMono.just(CalendarEventParseResults.notParse(e.blobId))
        case _ => SMono.just(CalendarEventParseResults.notParse(blobId))
      }
}