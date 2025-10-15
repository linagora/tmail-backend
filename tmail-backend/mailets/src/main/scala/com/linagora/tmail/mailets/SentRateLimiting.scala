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

import com.linagora.tmail.mailets.TmailMailRateLimiter.createRateLimiter
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.EMPTY_RATE_LIMIT
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.rate.limiter.api.{AcceptableRate, RateExceeded, RateLimiterFactory, RateLimitingResult}
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
 * </ul>
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
  }

  override def service(mail: Mail): Unit =
    mail.getMaybeSender.asOptional()
      .ifPresent(sender => applySenderRateLimit(Username.fromMailAddress(sender), mail))

  private def applySenderRateLimit(sender: Username, mail: Mail): Unit = {
    val rateLimitingResult: RateLimitingResult = applySenderRateLimit(sender).block(rateLimiterTimeout.toScala)

    if (rateLimitingResult.equals(RateExceeded)) {
      mail.setState(exceededProcessor)
    }
  }

  private def applySenderRateLimit(sender: Username): SMono[RateLimitingResult] =
    SMono.fromPublisher(rateLimitingRepository.getRateLimiting(sender))
      .flatMap(userRateLimitingDefinition => getDomainRateLimiting(sender)
        .map(domainRateLimitingDefinition => createSenderRateLimiters(userRateLimitingDefinition, domainRateLimitingDefinition))
        .flatMapMany(SFlux.fromIterable)
        .flatMap(rateLimiter => SMono.fromPublisher(rateLimiter.rateLimit(sender)))
        .fold[RateLimitingResult](AcceptableRate)((a, b) => a.merge(b)))

  private def getDomainRateLimiting(recipient: Username): SMono[RateLimitingDefinition] =
    recipient.getDomainPart.toScala match {
      case None => SMono.just(EMPTY_RATE_LIMIT)
      case Some(domain) => SMono.fromPublisher(rateLimitingRepository.getRateLimiting(domain))
    }

  private def createSenderRateLimiters(userRateLimitingDefinition: RateLimitingDefinition, domainRateLimitingDefinition: RateLimitingDefinition): Seq[TmailMailRateLimiter] = {
    val mailsSentPerMinuteLimit: Option[Long] = userRateLimitingDefinition.mailsSentPerMinute().toScala
      .map(Long2long)
      .orElse(domainRateLimitingDefinition.mailsSentPerMinute().toScala
        .map(Long2long)
        .orElse(mailsPerMinuteDefault))
    val mailsSentPerHourLimit: Option[Long] = userRateLimitingDefinition.mailsSentPerHours().toScala
      .map(Long2long)
      .orElse(domainRateLimitingDefinition.mailsSentPerHours().toScala
        .map(Long2long)
        .orElse(mailsPerHourDefault))
    val mailsSentPerDayLimit: Option[Long] = userRateLimitingDefinition.mailsSentPerDays().toScala
      .map(Long2long)
      .orElse(domainRateLimitingDefinition.mailsSentPerDays().toScala
        .map(Long2long)
        .orElse(mailsPerDayDefault))

    Seq(
      createRateLimiter(rateLimiterFactory, MailsSentPerMinuteType, mailsSentPerMinuteLimit, precision, keyPrefix),
      createRateLimiter(rateLimiterFactory, MailsSentPerHourType, mailsSentPerHourLimit, precision, keyPrefix),
      createRateLimiter(rateLimiterFactory, MailsSentPerDayType, mailsSentPerDayLimit, precision, keyPrefix))
      .flatten
  }
}
