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

import com.google.common.collect.ImmutableMap
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.json.ForwardSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FORWARD
import com.linagora.tmail.james.jmap.model.{ForwardId, ForwardSetError, ForwardSetRequest, ForwardSetResponse, ForwardSetUpdateFailure, ForwardSetUpdateResult, ForwardSetUpdateResults, ForwardSetUpdateSuccess, ForwardUpdateRequest}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.{SetErrorDescription, invalidArgumentValue}
import org.apache.james.jmap.core.{Invocation, SessionTranslator, UuidState}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.rrt.api.RecipientRewriteTable
import org.apache.james.rrt.lib.Mapping.Type
import org.apache.james.rrt.lib.MappingSource
import org.apache.james.util.{AuditTrail, ReactorUtils}
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters._
import scala.jdk.StreamConverters.StreamHasToScala

class ForwardSetMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[ForwardSetMethod])
  }
}

class ForwardSetMethod @Inject()(recipientRewriteTable: RecipientRewriteTable,
                                 val metricFactory: MetricFactory,
                                 val sessionTranslator: SessionTranslator,
                                 val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[ForwardSetRequest] {

  override val methodName: MethodName = MethodName("Forward/set")
  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FORWARD)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, ForwardSetRequest] =
    ForwardSerializer.deserializeForwardSetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: ForwardSetRequest): Publisher[InvocationWithContext] = {
    DelegatedAccountPrecondition.acceptOnlyOwnerRequest(mailboxSession, request.accountId)
    updateForward(request, mailboxSession)
      .map(updateResult => createResponse(invocation.invocation, request, updateResult))
      .map(InvocationWithContext(_, invocation.processingContext))
  }

  private def updateForward(request: ForwardSetRequest, mailboxSession: MailboxSession): SMono[ForwardSetUpdateResults] =
    SFlux.fromIterable(request.parseUpdateRequest())
      .flatMap {
        case (id, Right(patch)) => patch.asForwardUpdateRequest
          .fold(error => SMono.just(ForwardSetUpdateFailure(id, error)),
            validPath => update(MappingSource.fromUser(mailboxSession.getUser), getForwards(validPath, mailboxSession.getUser)))
        case (id, Left(error)) => SMono.just(ForwardSetUpdateFailure(id, error))
      }
      .map(_.asForwardSetUpdateResult)
      .foldWith[ForwardSetUpdateResults](ForwardSetUpdateResults.empty())(ForwardSetUpdateResults.merge)

  private def getForwards(updateRequest: ForwardUpdateRequest, currentUser: Username): Seq[MailAddress] = {
    if (updateRequest.localCopy.value.booleanValue()) {
      updateRequest.forwards.map(_.value) :+ currentUser.asMailAddress()
    } else {
      updateRequest.forwards.map(_.value)
    }
  }

  private def createResponse(invocation: Invocation,
                             forwardSetRequest: ForwardSetRequest,
                             updateResult: ForwardSetUpdateResults): Invocation = {
    val response: ForwardSetResponse = ForwardSetResponse(
      accountId = forwardSetRequest.accountId,
      newState = UuidState.INSTANCE,
      updated = Some(updateResult.updateSuccess).filter(_.nonEmpty),
      notUpdated = Some(updateResult.updateFailures).filter(_.nonEmpty),
      notCreated = validateNoCreate(forwardSetRequest),
      notDestroyed = validateNoDestroy(forwardSetRequest))

    Invocation(methodName,
      Arguments(ForwardSerializer.serializeForwardSetResponse(response).as[JsObject]),
      invocation.methodCallId)
  }

  private def update(mappingSource: MappingSource, forwardDestinations: Seq[MailAddress]): SMono[ForwardSetUpdateResult] =
    retrieveMappings(mappingSource)
      .map(currentForwards => (currentForwards.diff(forwardDestinations), forwardDestinations.diff(currentForwards)))
      .flatMapMany {
        case (deletedForwards, addedForwards) => deleteMappings(mappingSource, deletedForwards)
          .thenMany(addMappings(mappingSource, addedForwards))
      }.`then`()
      .doOnSuccess(_ -> AuditTrail.entry()
        .username(() => mappingSource.asUsername().toScala.map(_.asString()).getOrElse(""))
        .protocol("JMAP")
        .action("ForwardSet")
        .parameters(() => ImmutableMap.of("mappingSource", mappingSource.asUsername().toScala.map(_.asString()).getOrElse(""),
          "forwardList", forwardDestinations.map(_.asString).mkString(",")))
        .log("Update forward."))
      .`then`(SMono.just[ForwardSetUpdateResult](ForwardSetUpdateSuccess))
      .onErrorResume(error => SMono.just[ForwardSetUpdateResult](ForwardSetUpdateFailure(ForwardId.asString, error)))

  private def retrieveMappings(mappingSource: MappingSource): SMono[Seq[MailAddress]] =
    SMono.fromCallable(() => recipientRewriteTable.getStoredMappings(mappingSource)
      .select(Type.Forward)
      .asStream()
      .map[MailAddress](mapping => mapping.asMailAddress()
        .orElseThrow(() => new IllegalStateException(s"Can not compute address for mapping ${mapping.asString}")))
      .toScala(Seq))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  private def deleteMappings(source: MappingSource, forwards: Seq[MailAddress]): SFlux[MailAddress] =
    SFlux.fromIterable(forwards)
      .map(forwardDestination => {
        recipientRewriteTable.removeForwardMapping(source, forwardDestination.asString())
        forwardDestination
      })

  private def addMappings(source: MappingSource, forwards: Seq[MailAddress]): SFlux[MailAddress] =
    SFlux.fromIterable(forwards)
      .map(forwardDestination => {
        recipientRewriteTable.addForwardMapping(source, forwardDestination.asString())
        forwardDestination
      })

  private def validateNoCreate(forwardSetRequest: ForwardSetRequest): Option[Map[String, ForwardSetError]] =
    forwardSetRequest.create.map(aMap => aMap
      .view
      .mapValues(_ => ForwardSetError(invalidArgumentValue, Some(SetErrorDescription("'create' is not supported on singleton objects"))))
      .toMap)


  private def validateNoDestroy(forwardSetRequest: ForwardSetRequest): Option[Map[String, ForwardSetError]] =
    forwardSetRequest.destroy.map(aSet =>
      aSet.map(id => (id, ForwardSetError(invalidArgumentValue, Some(SetErrorDescription("'destroy' is not supported on singleton objects")))))
        .toMap)

}
