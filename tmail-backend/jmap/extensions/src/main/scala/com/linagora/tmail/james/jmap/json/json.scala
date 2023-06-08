/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package com.linagora.tmail.james.jmap

import eu.timepit.refined.api.{RefType, Validate}
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Properties, SetError, UTCDate}
import play.api.libs.json._

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

package object json {
  implicit def writeRefined[T, P, F[_, _]](
                                            implicit writesT: Writes[T],
                                            reftype: RefType[F]
                                          ): Writes[F[T, P]] = Writes(value => writesT.writes(reftype.unwrap(value)))
  implicit def readRefined[T, P, F[_, _]](
                                           implicit readsT: Reads[T],
                                           reftype: RefType[F],
                                           validate: Validate[T, P]
                                         ): Reads[F[T, P]] =
    Reads(jsValue =>
      readsT.reads(jsValue).flatMap { valueT =>
        reftype.refine[P](valueT) match {
          case Right(valueP) => JsSuccess(valueP)
          case Left(error)   => JsError(error)
        }
      })

  implicit val accountId: Format[AccountId] = Json.valueFormat[AccountId]
  implicit val propertiesFormat: Format[Properties] = Json.valueFormat[Properties]
  implicit val setErrorDescriptionWrites: Writes[SetErrorDescription] = Json.valueWrites[SetErrorDescription]
  implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
  val dateTimeUTCFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")

  implicit val timeStampFieldWrites: Writes[ZonedDateTime] = time => JsString(time.format(dateTimeFormatter))

  implicit val utcDateWrites : Writes[UTCDate] = utcDate => JsString(utcDate.asUTC.format(dateTimeUTCFormatter))

  implicit val UTCDateReads: Reads[UTCDate] = {
    case JsString(value) =>
      Try(UTCDate(ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME))) match {
        case Success(value) => JsSuccess(value)
        case Failure(e) => JsError(e.getMessage)
      }
    case _ => JsError("Expecting js string to represent UTC Date")
  }

  implicit val mailAddressReads: Reads[MailAddress] = {
    case JsString(value) => Try(JsSuccess(new MailAddress(value)))
      .fold(e => JsError(s"Invalid mailAddress: ${e.getMessage}"), mailAddress => mailAddress)
    case _ => JsError("Expecting mailAddress to be represented by a JsString")
  }

  implicit val mailAddressWrites: Writes[MailAddress] = mail => JsString(mail.toString)

}
