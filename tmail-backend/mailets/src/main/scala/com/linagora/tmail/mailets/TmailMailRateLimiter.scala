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

package com.linagora.tmail.mailets

import java.time.Duration

import com.linagora.tmail.mailets.MailLimit.UNLIMITED
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.rate.limiter.api.AllowedQuantity.AllowedQuantity
import org.apache.james.rate.limiter.api.{AllowedQuantity, RateLimiter, RateLimiterFactory, RateLimitingKey, RateLimitingResult, Rule, Rules}
import org.apache.james.transport.mailets.KeyPrefix
import org.reactivestreams.Publisher

object TmailMailRateLimiter {
  def createRateLimiter(rateLimiterFactory: RateLimiterFactory, mailLimitType: MailLimitType, limit: Option[Long], precision: Option[Duration], keyPrefix: Option[KeyPrefix]): Option[TmailMailRateLimiter] =
    limit match {
      case None => None
      case Some(UNLIMITED) => None
      case Some(value) =>
        val allowedQuantity: AllowedQuantity = AllowedQuantity.validate(value) match {
          case Right(value) => value
          case Left(exception) => throw new IllegalArgumentException(s"Invalid limit for ${mailLimitType.asString}: $limit", exception)
        }

        Some(TmailMailRateLimiter(
          rateLimiter = rateLimiterFactory.withSpecification(
            rules = Rules(Seq(Rule(allowedQuantity, mailLimitType.duration))),
            precision = precision),
          keyPrefix = keyPrefix,
          mailLimitType = mailLimitType))
    }
}

case class TmailMailRateLimiter(rateLimiter: RateLimiter,
                                keyPrefix: Option[KeyPrefix] = None,
                                mailLimitType: MailLimitType) {

  def rateLimit(username: Username): Publisher[RateLimitingResult] =
    rateLimiter.rateLimit(
      key = MailRateLimitingKey(
        keyPrefix = keyPrefix,
        mailLimitType = mailLimitType,
        username = username),
      increaseQuantity = 1)
}

case class MailRateLimitingKey(keyPrefix: Option[KeyPrefix],
                               mailLimitType: MailLimitType,
                               username: Username) extends RateLimitingKey {
  override def asString(): String = s"${
    keyPrefix.map(prefix => prefix.value + "_")
      .getOrElse("")
  }${mailLimitType.asString}_${username.asString()}"
}

sealed trait MailLimitType {
  def asString: String

  def duration: Duration
}

case object MailsSentPerMinuteType extends MailLimitType {
  override val asString: String = "mailsSentPerMinute"

  override val duration: Duration = Duration.ofMinutes(1)
}

case object MailsSentPerHourType extends MailLimitType {
  override val asString: String = "mailsSentPerHour"

  override val duration: Duration = Duration.ofHours(1)
}

case object MailsSentPerDayType extends MailLimitType {
  override val asString: String = "mailsSentPerDay"

  override val duration: Duration = Duration.ofDays(1)
}

case object MailsReceivedPerMinuteType extends MailLimitType {
  override val asString: String = "mailsReceivedPerMinute"

  override val duration: Duration = Duration.ofMinutes(1)
}

case object MailsReceivedPerHourType extends MailLimitType {
  override val asString: String = "mailsReceivedPerHour"

  override val duration: Duration = Duration.ofHours(1)
}

case object MailsReceivedPerDayType extends MailLimitType {
  override val asString: String = "mailsReceivedPerDay"

  override val duration: Duration = Duration.ofDays(1)
}

object MailLimit {
  val UNLIMITED: Long = -1L
}