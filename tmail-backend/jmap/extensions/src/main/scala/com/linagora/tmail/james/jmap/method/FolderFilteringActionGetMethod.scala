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

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Inject}
import com.linagora.tmail.james.jmap.json.FolderFilteringActionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FILTER
import com.linagora.tmail.james.jmap.model._
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{Invocation, UnsignedInt}
import org.apache.james.jmap.method.{InvocationWithContext, Method, MethodWithoutAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.{MailboxManager, MailboxSession}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.task.{TaskExecutionDetails, TaskId, TaskManager, TaskNotFoundException}
import org.apache.james.util.ReactorUtils
import org.apache.james.webadmin.data.jmap.RunRulesOnMailboxTask
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

class FolderFilteringActionMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[FolderFilteringActionGetMethod])
  }
}

class FolderFilteringActionGetMethod @Inject()()(val taskManager: TaskManager,
                                                 val mailboxManager: MailboxManager,
                                                 val metricFactory: MetricFactory,
                                                 val sessionSupplier: SessionSupplier) extends MethodWithoutAccountId[FolderFilteringActionGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("FolderFilteringAction/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_FILTER)

  override def getRequest(invocation: Invocation): Either[Exception, FolderFilteringActionGetRequest] =
    FolderFilteringActionSerializer.deserializeGetRequest(invocation.arguments.value).asEitherRequest

  override def doProcess(invocation: InvocationWithContext, mailboxSession: MailboxSession, request: FolderFilteringActionGetRequest): Publisher[InvocationWithContext] =
    request.validateProperties
      .fold(e => SMono.error(e),
        properties => retrieveFolderFilteringActions(mailboxSession, request.ids)
          .collectSeq()
          .map(actions => FolderFilteringActionGetResponse.from(actions, request.ids))
          .map(response => Invocation(
            methodName = methodName,
            arguments = Arguments(FolderFilteringActionSerializer.serializeGetResponse(response, properties).as[JsObject]),
            methodCallId = invocation.invocation.methodCallId))
          .map(invocationResult => InvocationWithContext(invocationResult, invocation.processingContext)))

  private def retrieveFolderFilteringActions(mailboxSession: MailboxSession, ids: FolderFilteringActionIds): SFlux[FolderFilteringAction] =
    SFlux.fromIterable(filteringValidTaskIds(ids))
      .flatMap(taskId => SMono.fromCallable(() => taskManager.getExecutionDetails(taskId))
        .onErrorResume {
          case _: TaskNotFoundException => SMono.empty
          case e => SMono.error(e)
        }
        .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
      .filter(taskDetail => isRunRulesOnMailboxTask(taskDetail) && belongsToUser(mailboxSession.getUser, taskDetail))
      .map(toFolderFilteringAction)

  private def filteringValidTaskIds(ids: FolderFilteringActionIds): List[TaskId] =
    ids.list.map(unparsedId => FolderFilteringTaskIdUtil.liftOrThrow(unparsedId).toOption)
      .filter(_.isDefined)
      .map(_.get)

  private def isRunRulesOnMailboxTask(taskDetail: TaskExecutionDetails): Boolean =
    taskDetail.getType.equals(RunRulesOnMailboxTask.TASK_TYPE)

  private def belongsToUser(username: Username, taskDetail: TaskExecutionDetails): Boolean =
    taskDetail.getAdditionalInformation.get()
      .asInstanceOf[RunRulesOnMailboxTask.AdditionalInformation]
      .getUsername
      .equals(username)

  private def toFolderFilteringAction(taskDetail: TaskExecutionDetails): FolderFilteringAction = {
    val info: RunRulesOnMailboxTask.AdditionalInformation = taskDetail.getAdditionalInformation.get()
      .asInstanceOf[RunRulesOnMailboxTask.AdditionalInformation]
    val processedCount: Long = info.getProcessedMessagesCount
    val successCount: Long = info.getRulesOnMessagesApplySuccessfully
    val failedCount: Long = info.getRulesOnMessagesApplyFailed
    val maximumReached: Boolean = info.maximumAppliedActionExceeded()

    FolderFilteringAction(
      id = taskDetail.getTaskId,
      status = taskDetail.getStatus,
      processedMessageCount = ProcessedMessageCount(UnsignedInt.liftOrThrow(processedCount)),
      successfulActions = SuccessfulActions(UnsignedInt.liftOrThrow(successCount)),
      failedActions = FailedActions(UnsignedInt.liftOrThrow(failedCount)),
      maximumAppliedActionReached = maximumReached)
  }
}
