package com.linagora.tmail.encrypted

import java.util.Optional
import java.{lang, util}

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.mailbox.MailboxManager.{MailboxCapabilities, MailboxRenamedResult, MessageCapabilities, SearchCapabilities}
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.model.{Mailbox, MailboxACL, MailboxAnnotation, MailboxAnnotationKey, MailboxId, MailboxMetaData, MailboxPath, MessageId, MessageRange, MultimailboxesSearchQuery}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MessageManager}
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

class EncryptedMailboxManager @Inject()(mailboxManager: MailboxManager,
                                        keystoreManager: KeystoreManager,
                                        clearEmailContentFactory: ClearEmailContentFactory,
                                        encryptedEmailContentStore: EncryptedEmailContentStore) extends MailboxManager {

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

  override def mailboxExists(mailboxPath: MailboxPath, session: MailboxSession): Publisher[lang.Boolean] = mailboxManager.mailboxExists(mailboxPath, session)

  override def list(session: MailboxSession): util.List[MailboxPath] = mailboxManager.list(session)

  override def hasChildren(mailboxPath: MailboxPath, session: MailboxSession): Boolean = mailboxManager.hasChildren(mailboxPath, session)

  override def startProcessingRequest(session: MailboxSession): Unit = mailboxManager.startProcessingRequest(session)

  override def endProcessingRequest(session: MailboxSession): Unit = mailboxManager.endProcessingRequest(session)

  override def getAllAnnotations(mailboxPath: MailboxPath, session: MailboxSession): util.List[MailboxAnnotation] =
    mailboxManager.getAllAnnotations(mailboxPath, session)

  override def getAnnotationsByKeys(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): util.List[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeys(mailboxPath, session, keys)

  override def getAnnotationsByKeysWithOneDepth(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): util.List[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeysWithOneDepth(mailboxPath, session, keys)

  override def getAnnotationsByKeysWithAllDepth(mailboxPath: MailboxPath, session: MailboxSession, keys: util.Set[MailboxAnnotationKey]): util.List[MailboxAnnotation] =
    mailboxManager.getAnnotationsByKeysWithAllDepth(mailboxPath, session, keys)

  override def updateAnnotations(mailboxPath: MailboxPath, session: MailboxSession, mailboxAnnotations: util.List[MailboxAnnotation]): Unit =
    mailboxManager.updateAnnotations(mailboxPath, session, mailboxAnnotations)

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

  override def getDelimiter: Char = mailboxManager.getDelimiter

  override def createSystemSession(userName: Username): MailboxSession = mailboxManager.createSystemSession(userName)

  override def login(userid: Username, passwd: String): MailboxSession = mailboxManager.login(userid, passwd)

  override def loginAsOtherUser(adminUserid: Username, passwd: String, otherUserId: Username): MailboxSession =
    mailboxManager.loginAsOtherUser(adminUserid, passwd, otherUserId)

  override def logout(session: MailboxSession): Unit = mailboxManager.logout(session)

  override def getMailboxReactive(mailboxId: MailboxId, session: MailboxSession): Publisher[MessageManager] = mailboxManager.getMailboxReactive(mailboxId, session)

  override def getMailboxReactive(mailboxPath: MailboxPath, session: MailboxSession): Publisher[MessageManager] = mailboxManager.getMailboxReactive(mailboxPath, session)
}
