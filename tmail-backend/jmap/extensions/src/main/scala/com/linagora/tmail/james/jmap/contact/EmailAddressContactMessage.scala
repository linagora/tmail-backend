package com.linagora.tmail.james.jmap.contact

import java.util.Locale

import com.linagora.tmail.james.jmap.contact.TmailContactMessageScope.{DOMAIN, USER}
import com.linagora.tmail.james.jmap.contact.TmailContactMessageType.{ADDITION, REMOVAL, UPDATE}
import org.apache.james.core.{Domain, MailAddress, Username}

import scala.util.Try

case class EmailAddressContactMessage(messageType: TmailContactMessageType,
                                      scope: TmailContactMessageScope,
                                      owner: ContactOwner,
                                      entry: MessageEntry)

object TmailContactMessageType {
  val ADDITION: String = "addition"
  val REMOVAL: String = "removal"
  val UPDATE: String = "update"

  def from(value: String): Either[IllegalArgumentException, TmailContactMessageType] =
    value.toLowerCase(Locale.US) match {
      case ADDITION => Right(Addition)
      case REMOVAL => Right(Removal)
      case UPDATE => Right(Update)
      case _ => Left(new IllegalArgumentException(s"`$value` is invalid"))
    }
}

trait TmailContactMessageType {
  def value: String
}

case object Addition extends TmailContactMessageType {
  override def value: String = ADDITION
}

case object Removal extends TmailContactMessageType {
  override def value: String = REMOVAL
}

case object Update extends TmailContactMessageType {
  override def value: String = UPDATE
}

object TmailContactMessageScope {
  val USER: String = "user"
  val DOMAIN: String = "domain"

  def from(value: String): Either[IllegalArgumentException, TmailContactMessageScope] =
    value.toLowerCase(Locale.US) match {
      case USER => Right(User)
      case DOMAIN => Right(Domain)
      case _ => Left(new IllegalArgumentException(s"`$value` is invalid"))
    }
}

trait TmailContactMessageScope {
  def value: String
}

case object User extends TmailContactMessageScope {
  override def value: String = USER
}

case object Domain extends TmailContactMessageScope {
  override def value: String = DOMAIN
}

object ContactOwner {
  def asUsername(owner: ContactOwner): Either[IllegalArgumentException, Username] =
    Try(Username.of(owner.value)).toEither match {
      case Left(value) => Left(new IllegalArgumentException(value.getMessage))
      case Right(value) => Right(value)
    }

  def asDomain(owner: ContactOwner): Either[IllegalArgumentException, Domain] =
    Try(org.apache.james.core.Domain.of(owner.value)).toEither match {
      case Left(value) => Left(new IllegalArgumentException(value.getMessage))
      case Right(value) => Right(value)
    }
}

case class ContactOwner(value: String)

object MessageEntry {
  def toContactField(entry: MessageEntry): ContactFields =
    ContactFields(address = entry.address, firstname = entry.firstname.getOrElse(""), surname = entry.surname.getOrElse(""))
}

case class MessageEntry(address: MailAddress, firstname: Option[String], surname: Option[String])