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

package com.linagora.tmail.encrypted

import java.util.Optional
import java.{lang, util}

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.mailbox.MailboxManager.{MailboxCapabilities, MailboxRenamedResult, MessageCapabilities, SearchCapabilities}
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{Mailbox, MailboxACL, MailboxAnnotation, MailboxAnnotationKey, MailboxId, MailboxMetaData, MailboxPath, MessageId, MessageRange, MultimailboxesSearchQuery, ThreadId}
import org.apache.james.mailbox.{Authorizator, MailboxManager, MailboxSession, MessageManager, SessionProvider}
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono

class EncryptedMailboxManager @Inject()(mailboxManager: MailboxManager,
                                        keystoreManager: KeystoreManager,
                                        clearEmailContentFactory: ClearEmailContentFactory,
                                        encryptedEmailContentStore: EncryptedEmailContentStore) extends MailboxManager {

  override def withExtraAuthorizator(authorizator: Authorizator): SessionProvider = mailboxManager.withExtraAuthorizator(authorizator)

  override def getMailbox(mailbox: Mailbox, session: MailboxSession): MessageManager = mailboxManager.getMailbox(mailbox, session)

  override def getSupportedMailboxCapabilities: util.EnumSet[MailboxCapabilities] = mailboxManager.getSupportedMailboxCapabilities

  override def hasCapability(capability: MailboxCapabilities): Boolean = mailboxManager.hasCapability(capability)

  override def getSupportedMessageCapabilities: util.EnumSet[MessageCapabilities] = mailboxManager.getSupportedMessageCapabilities

  override def getSupportedSearchCapabilities: util.EnumSet[SearchCapabilities] = mailboxManager.getSupportedSearchCapabilities

  override def getMailbox(mailboxPath: MailboxPath, session: MailboxSession): MessageManager = new EncryptedMessageManager(mailboxManager.getMailbox(mailboxPath, session), keystoreManager, clearEmailContentFactory, encryptedEmailContentStore)

  override def getMailbox(mailboxId: MailboxId, session: MailboxSession): MessageManager = new EncryptedMessageManager(mailboxManager.getMailbox(mailboxId, session), keystoreManager, clearEmailContentFactory, encryptedEmailContentStore)

  override def createMailbox(mailboxPath: MailboxPath, mailboxSession: MailboxSession): Optional[MailboxId] =
    mailboxManager.createMailbox(mailboxPath, mailboxSession)

  override def deleteMailbox(mailboxPath: MailboxPath, session: MailboxSession): Unit = mailboxManager.deleteMailbox(mailboxPath, session)

  override def deleteMailbox(mailboxId: MailboxId, session: MailboxSession): Mailbox = mailboxManager.deleteMailbox(mailboxId, session)

  override def renameMailbox(from: MailboxPath, to: MailboxPath, option: MailboxManager.RenameOption, session: MailboxSession): util.List[MailboxRenamedResult] =
    mailboxManager.renameMailbox(from, to, option, session)

  override def renameMailbox(mailboxId: MailboxId, newMailboxPath: MailboxPath, option: MailboxManager.RenameOption, session: MailboxSession): util.List[MailboxRenamedResult] =
    mailboxManager.renameMailbox(mailboxId, newMailboxPath, option, session)

  override def copyMessages(set: MessageRange, from: MailboxPath, to: MailboxPath, session: MailboxSession): util.List[MessageRange] =
    mailboxManager.copyMessages(set, from, to, session)

  override def copyMessages(set: MessageRange, from: MailboxId, to: MailboxId, session: MailboxSession): util.List[MessageRange] =
    mailboxManager.copyMessages(set, from, to, session)

  override def moveMessages(set: MessageRange, from: MailboxPath, to: MailboxPath, session: MailboxSession): util.List[MessageRange] =
    mailboxManager.moveMessages(set, from, to, session)

  override def moveMessages(set: MessageRange, from: MailboxId, to: MailboxId, session: MailboxSession): util.List[MessageRange] =
    mailboxManager.moveMessages(set, from, to, session)

  override def search(expression: MailboxQuery, fetchType: MailboxManager.MailboxSearchFetchType, session: MailboxSession): Flux[MailboxMetaData] =
    mailboxManager.search(expression, fetchType, session)

  override def search(expression: MultimailboxesSearchQuery, session: MailboxSession, limit: Long): Publisher[MessageId] =
    mailboxManager.search(expression, session, limit)

  override def getThread(threadId: ThreadId, session: MailboxSession): Publisher[MessageId] =
    mailboxManager.getThread(threadId, session)

  override def mailboxExists(mailboxPath: MailboxPath, session: MailboxSession): Publisher[lang.Boolean] = mailboxManager.mailboxExists(mailboxPath, session)

  override def list(session: MailboxSession): util.List[MailboxPath] = mailboxManager.list(session)

  override def hasChildren(mailboxPath: MailboxPath, session: MailboxSession): Boolean = mailboxManager.hasChildren(mailboxPath, session)

  override def startProcessingRequest(session: MailboxSession): Unit = mailboxManager.startProcessingRequest(session)

  override def endProcessingRequest(session: MailboxSession): Unit = mailboxManager.endProcessingRequest(session)

  override def getAllAnnotations(mailboxPath: MailboxPath, session: MailboxSession): util.List[MailboxAnnotation] =
    mailboxManager.getAllAnnotations(mailboxPath, session)

  override def getAllAnnotationsReactive(mailboxPath: MailboxPath, session: MailboxSession): Publisher[MailboxAnnotation] =
    mailboxManager.getAllAnnotationsReactive(mailboxPath, session)

  override def getAnnotationsByKeys(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): util.List[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeys(mailboxPath, session, keys)

  override def getAnnotationsByKeysReactive(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): Publisher[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeysReactive(mailboxPath, session, keys)

  override def getAnnotationsByKeysWithOneDepth(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): util.List[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeysWithOneDepth(mailboxPath, session, keys)

  override def getAnnotationsByKeysWithOneDepthReactive(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): Publisher[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeysWithOneDepthReactive(mailboxPath, session, keys)

  override def getAnnotationsByKeysWithAllDepth(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): util.List[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeysWithAllDepth(mailboxPath, session, keys)

  override def getAnnotationsByKeysWithAllDepthReactive(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): Publisher[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeysWithAllDepthReactive(mailboxPath, session, keys)

  override def updateAnnotations(mailboxPath: MailboxPath, session: MailboxSession, mailboxAnnotations: util.List[MailboxAnnotation]): Unit =
    mailboxManager.updateAnnotations(mailboxPath, session, mailboxAnnotations)

  override def updateAnnotationsReactive(mailboxPath: MailboxPath, session: MailboxSession, mailboxAnnotations: util.List[MailboxAnnotation]): Publisher[Void] =
    mailboxManager.updateAnnotationsReactive(mailboxPath, session, mailboxAnnotations)

  override def hasRight(mailboxPath: MailboxPath, right: MailboxACL.Right, session: MailboxSession): Boolean =
    mailboxManager.hasRight(mailboxPath, right, session)

  override def hasRight(mailboxId: MailboxId, right: MailboxACL.Right, session: MailboxSession): Boolean = mailboxManager.hasRight(mailboxId, right, session)

  override def listRights(mailboxPath: MailboxPath, identifier: MailboxACL.EntryKey, session: MailboxSession): util.List[MailboxACL.Rfc4314Rights] =
    mailboxManager.listRights(mailboxPath, identifier, session)

  override def listRights(mailboxPath: MailboxPath, session: MailboxSession): MailboxACL = mailboxManager.listRights(mailboxPath, session)

  override def listRights(mailboxId: MailboxId, session: MailboxSession): MailboxACL = mailboxManager.listRights(mailboxId, session)

  override def myRights(mailboxPath: MailboxPath, session: MailboxSession): MailboxACL.Rfc4314Rights = mailboxManager.myRights(mailboxPath, session)

  override def myRights(mailboxId: MailboxId, session: MailboxSession): Publisher[MailboxACL.Rfc4314Rights] = mailboxManager.myRights(mailboxId, session)

  override def myRights(mailbox: Mailbox, session: MailboxSession): MailboxACL.Rfc4314Rights = mailboxManager.myRights(mailbox, session)

  override def applyRightsCommand(mailboxPath: MailboxPath, mailboxACLCommand: MailboxACL.ACLCommand, session: MailboxSession): Unit =
    mailboxManager.applyRightsCommand(mailboxPath, mailboxACLCommand, session)

  override def applyRightsCommand(mailboxId: MailboxId, mailboxACLCommand: MailboxACL.ACLCommand, session: MailboxSession): Unit =
    mailboxManager.applyRightsCommand(mailboxId, mailboxACLCommand, session)

  override def setRights(mailboxPath: MailboxPath, mailboxACL: MailboxACL, session: MailboxSession): Unit = mailboxManager.setRights(mailboxPath, mailboxACL, session)

  override def setRights(mailboxId: MailboxId, mailboxACL: MailboxACL, session: MailboxSession): Unit = mailboxManager.setRights(mailboxId, mailboxACL, session)

  override def createSystemSession(userName: Username): MailboxSession = mailboxManager.createSystemSession(userName)

  override def getMailboxReactive(mailboxId: MailboxId, session: MailboxSession): Publisher[MessageManager] =
    SMono.fromPublisher(mailboxManager.getMailboxReactive(mailboxId, session))
      .map(messageManager => new EncryptedMessageManager(messageManager, keystoreManager, clearEmailContentFactory, encryptedEmailContentStore))

  override def getMailboxReactive(mailboxPath: MailboxPath, session: MailboxSession): Publisher[MessageManager] =
    SMono.fromPublisher(mailboxManager.getMailboxReactive(mailboxPath, session))
      .map(messageManager => new EncryptedMessageManager(messageManager, keystoreManager, clearEmailContentFactory, encryptedEmailContentStore))

  override def hasRight(mailbox: Mailbox, right: MailboxACL.Right, session: MailboxSession): Boolean = mailboxManager.hasRight(mailbox, right, session)

  override def listRights(mailbox: Mailbox, identifier: MailboxACL.EntryKey, session: MailboxSession): util.List[MailboxACL.Rfc4314Rights] =
    mailboxManager.listRights(mailbox, identifier, session)

  override def hasChildrenReactive(mailboxPath: MailboxPath, session: MailboxSession): Publisher[lang.Boolean] = mailboxManager.hasChildrenReactive(mailboxPath, session)

  override def getMessageIdFactory: MessageId.Factory = mailboxManager.getMessageIdFactory

  override def authenticate(givenUserid: Username, passwd: String): SessionProvider.AuthorizationStep =
    mailboxManager.authenticate(givenUserid, passwd)

  override def authenticate(givenUserid: Username): SessionProvider.AuthorizationStep =
    mailboxManager.authenticate(givenUserid)

  override def createMailbox(mailboxPath: MailboxPath, createOption: MailboxManager.CreateOption, mailboxSession: MailboxSession): Optional[MailboxId] =
    mailboxManager.createMailbox(mailboxPath, createOption, mailboxSession)

  override def hasRightReactive(mailboxPath: MailboxPath, right: MailboxACL.Right, session: MailboxSession): Publisher[lang.Boolean] =
    mailboxManager.hasRightReactive(mailboxPath, right, session)

  override def myRightsReactive(mailboxPath: MailboxPath, session: MailboxSession): Publisher[MailboxACL.Rfc4314Rights] =
    mailboxManager.myRightsReactive(mailboxPath, session)

  override def applyRightsCommandReactive(mailboxPath: MailboxPath, mailboxACLCommand: MailboxACL.ACLCommand, session: MailboxSession): Publisher[Void] =
    mailboxManager.applyRightsCommandReactive(mailboxPath, mailboxACLCommand, session)

  override def listRightsReactive(mailboxPath: MailboxPath, session: MailboxSession): Publisher[MailboxACL] =
    mailboxManager.listRightsReactive(mailboxPath, session)

  override def listRightsReactive(mailboxId: MailboxId, session: MailboxSession): Publisher[MailboxACL] =
    mailboxManager.listRightsReactive(mailboxId, session)
}
