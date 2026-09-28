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
import java.time.temporal.ChronoUnit
import java.util.Optional

import com.linagora.tmail.mailets.TmailMailRateLimiter.createRateLimiter
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.EMPTY_RATE_LIMIT
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.rate.limiter.api.Increment.Increment
import org.apache.james.rate.limiter.api.{AcceptableRate, Increment, RateExceeded, RateLimiterFactory, RateLimitingResult}
import org.apache.james.transport.mailets.ConfigurationOps.OptionOps
import org.apache.james.transport.mailets.KeyPrefix
import org.apache.james.util.DurationParser
import org.apache.mailet.Mail
import org.apache.mailet.base.GenericMailet
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.DurationConverters._
import scala.jdk.OptionConverters._

/**
 * <p><b>SentRateLimiting</b> allows defining and enforcing rate limits for the sender of matching email.</p>
 *
 * <ul>This allows enforcing rules like:
 * <li>A sender can send at most 10 emails per minute</li>
 * <li>A sender can send at most 100 emails per hour</li>
 * <li>A sender can send at most 1000 emails per day</li>
 * <li>A sender can address at most 500 recipients per hour (each email counts for its number of recipients)</li>
 * </ul>
 *
 * <p>The rate limiting values are primarily determined by the {@code RateLimitingRepository}, which stores
 * rate limits (for example, set by an administrator or derived from a SaaS subscription plan).
 * If no stored rate limits exist, the mailet falls back to the configured default limits.</p>
 *
 * <ul>Here are supported configuration parameters:
 * <li><b>keyPrefix</b>: An optional key prefix to apply to rate limiting. Choose distinct values if you specify
 * this mailet twice within your <code>mailetcontainer.xml</code> file. Defaults to none.</li>
 * <li><b>exceededProcessor</b>: Processor to which emails whose rate is exceeded should be redirected. Defaults to <code>error</code>.
 * Use this to customize the behaviour upon exceeded rate.</li>
 * <li><b>precision</b>: [Optional, duration]. Duration granularity used for rate limiter. Default to the second unit if unit is not specified.</li>
 * <li><b>rateLimiterTimeout</b>: [Optional, duration, default: 5s]. Specifies the timeout for rate limiter checks. Default to the second unit if unit is not specified.</li>
 * <li><b>mailsPerMinuteDefault</b>: [Optional, long]. Default number of emails a sender is allowed to send per minute if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * <li><b>mailsPerHourDefault</b>: [Optional, long]. Default number of emails a sender is allowed to send per hour if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * <li><b>mailsPerDayDefault</b>: [Optional, long]. Default number of emails a sender is allowed to send per day if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * <li><b>recipientsPerMinuteDefault</b>: [Optional, long]. Default number of recipients a sender is allowed to address per minute if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * <li><b>recipientsPerHourDefault</b>: [Optional, long]. Default number of recipients a sender is allowed to address per hour if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * <li><b>recipientsPerDayDefault</b>: [Optional, long]. Default number of recipients a sender is allowed to address per day if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * </ul>
 *
 * <p>Recipients are the ones of the email when it reaches this mailet: place it after recipient rewriting and in the
 * relay processor in order to only account remote recipients. An email with more recipients than a recipient limit is
 * always rejected.</p>
 *
 * <p>For instance:</p>
 *
 *   <pre><code>
 * &lt;mailet matcher=&quot;All&quot; class=&quot;com.linagora.tmail.mailets.SentRateLimiting&quot;&gt;
 *     &lt;keyPrefix&gt;myPrefix&lt;/keyPrefix&gt;
 *     &lt;precision&gt;1s&lt;/precision&gt;
 *     &lt;mailsPerMinuteDefault&gt;10&lt;/mailsPerMinuteDefault&gt;
 *     &lt;mailsPerHourDefault&gt;100&lt;/mailsPerHourDefault&gt;
 *     &lt;mailsPerDayDefault&gt;1000&lt;/mailsPerDayDefault&gt;
 *     &lt;recipientsPerHourDefault&gt;500&lt;/recipientsPerHourDefault&gt;
 *     &lt;rateLimiterTimeout&gt;5s&lt;/rateLimiterTimeout&gt;
 *     &lt;exceededProcessor&gt;tooMuchMails&lt;/exceededProcessor&gt;
 * &lt;/mailet&gt;
 *   </code></pre>
 *
 */
class SentRateLimiting @Inject()(rateLimitingRepository: RateLimitingRepository,
                                 rateLimiterFactory: RateLimiterFactory) extends GenericMailet {

  private var exceededProcessor: String = _
  private var keyPrefix: Option[KeyPrefix] = None
  private var precision: Option[Duration] = None
  private var rateLimiterTimeout: Duration = _
  private var mailsPerMinuteDefault: Option[Long] = None
  private var mailsPerHourDefault: Option[Long]  = None
  private var mailsPerDayDefault: Option[Long] = None
  private var recipientsPerMinuteDefault: Option[Long] = None
  private var recipientsPerHourDefault: Option[Long] = None
  private var recipientsPerDayDefault: Option[Long] = None

  override def init(): Unit = {
    exceededProcessor = getInitParameter("exceededProcessor", Mail.ERROR)
    keyPrefix = Option(getInitParameter("keyPrefix")).map(KeyPrefix)
    precision = getMailetConfig.getOptionalString("precision")
      .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
    rateLimiterTimeout = getMailetConfig.getOptionalString("rateLimiterTimeout")
      .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
      .getOrElse(Duration.ofSeconds(5))
    mailsPerMinuteDefault = getMailetConfig.getOptionalLong("mailsPerMinuteDefault")
    mailsPerHourDefault = getMailetConfig.getOptionalLong("mailsPerHourDefault")
    mailsPerDayDefault = getMailetConfig.getOptionalLong("mailsPerDayDefault")
    recipientsPerMinuteDefault = getMailetConfig.getOptionalLong("recipientsPerMinuteDefault")
    recipientsPerHourDefault = getMailetConfig.getOptionalLong("recipientsPerHourDefault")
    recipientsPerDayDefault = getMailetConfig.getOptionalLong("recipientsPerDayDefault")
  }

  override def service(mail: Mail): Unit =
    mail.getMaybeSender.asOptional()
      .ifPresent(sender => applySenderRateLimit(Username.fromMailAddress(sender), mail))

  private def applySenderRateLimit(sender: Username, mail: Mail): Unit = {
    val rateLimitingResult: RateLimitingResult = applySenderRateLimit(sender, mail.getRecipients.size()).block(rateLimiterTimeout.toScala)

    if (rateLimitingResult.equals(RateExceeded)) {
      mail.setState(exceededProcessor)
    }
  }

  private def applySenderRateLimit(sender: Username, recipientCount: Int): SMono[RateLimitingResult] =
    SMono.fromPublisher(rateLimitingRepository.getRateLimiting(sender))
      .flatMap(userRateLimitingDefinition => getDomainRateLimiting(sender)
        .map(domainRateLimitingDefinition => createSenderRateLimiters(userRateLimitingDefinition, domainRateLimitingDefinition, recipientCount))
        .flatMapMany(SFlux.fromIterable)
        .flatMap { case (rateLimiter, increment) => SMono.fromPublisher(rateLimiter.rateLimit(sender, increment)) }
        .fold[RateLimitingResult](AcceptableRate)((a, b) => a.merge(b)))

  private def getDomainRateLimiting(recipient: Username): SMono[RateLimitingDefinition] =
    recipient.getDomainPart.toScala match {
      case None => SMono.just(EMPTY_RATE_LIMIT)
      case Some(domain) => SMono.fromPublisher(rateLimitingRepository.getRateLimiting(domain))
    }

  private def createSenderRateLimiters(user: RateLimitingDefinition, domain: RateLimitingDefinition, recipientCount: Int): Seq[(TmailMailRateLimiter, Increment)] = {
    val mailLimiters: Seq[TmailMailRateLimiter] = Seq(
      createRateLimiter(rateLimiterFactory, MailsSentPerMinuteType, resolveLimit(user.mailsSentPerMinute(), domain.mailsSentPerMinute(), mailsPerMinuteDefault), precision, keyPrefix),
      createRateLimiter(rateLimiterFactory, MailsSentPerHourType, resolveLimit(user.mailsSentPerHours(), domain.mailsSentPerHours(), mailsPerHourDefault), precision, keyPrefix),
      createRateLimiter(rateLimiterFactory, MailsSentPerDayType, resolveLimit(user.mailsSentPerDays(), domain.mailsSentPerDays(), mailsPerDayDefault), precision, keyPrefix))
      .flatten

    mailLimiters.map(limiter => (limiter, 1: Increment)) ++ createRecipientsRateLimiters(user, domain, recipientCount)
  }

  private def createRecipientsRateLimiters(user: RateLimitingDefinition, domain: RateLimitingDefinition, recipientCount: Int): Seq[(TmailMailRateLimiter, Increment)] =
    Increment.validate(recipientCount).toOption match {
      case None => Seq()
      case Some(increment) => Seq(
          createRateLimiter(rateLimiterFactory, RecipientsSentPerMinuteType, resolveLimit(user.recipientsSentPerMinute(), domain.recipientsSentPerMinute(), recipientsPerMinuteDefault), precision, keyPrefix),
          createRateLimiter(rateLimiterFactory, RecipientsSentPerHourType, resolveLimit(user.recipientsSentPerHours(), domain.recipientsSentPerHours(), recipientsPerHourDefault), precision, keyPrefix),
          createRateLimiter(rateLimiterFactory, RecipientsSentPerDayType, resolveLimit(user.recipientsSentPerDays(), domain.recipientsSentPerDays(), recipientsPerDayDefault), precision, keyPrefix))
        .flatten
        .map(limiter => (limiter, increment))
    }

  private def resolveLimit(userLimit: Optional[java.lang.Long], domainLimit: Optional[java.lang.Long], defaultLimit: Option[Long]): Option[Long] =
    userLimit.toScala.map(Long2long)
      .orElse(domainLimit.toScala.map(Long2long))
      .orElse(defaultLimit)
}
