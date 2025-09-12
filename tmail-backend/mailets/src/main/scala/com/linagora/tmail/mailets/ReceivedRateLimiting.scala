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

import com.google.common.collect.ImmutableList
import com.linagora.tmail.mailets.TmailMailRateLimiter.createRateLimiter
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition
import jakarta.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.lifecycle.api.LifecycleUtil
import org.apache.james.rate.limiter.api.{AcceptableRate, RateExceeded, RateLimiterFactory, RateLimitingResult}
import org.apache.james.transport.mailets.ConfigurationOps.OptionOps
import org.apache.james.transport.mailets.KeyPrefix
import org.apache.james.util.DurationParser
import org.apache.mailet.Mail
import org.apache.mailet.base.GenericMailet
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.jdk.OptionConverters._
import scala.util.Using

/**
 * <p><b>ReceivedRateLimiting</b> allows defining and enforcing rate limits for the recipients of matching email.</p>
 *
 * <ul>This allows enforcing rules like:
 * <li>A recipient can receive at most 10 emails per minute</li>
 * <li>A recipient can receive at most 100 emails per hour</li>
 * <li>A recipient can receive at most 1000 emails per day</li>
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
 * <li><b>precision</b>: [Optional, duration]. Duration granularity used for rate limiter. Defaults to the second unit if unit is not specified.</li>
 * <li><b>rateLimiterTimeout</b>: [Optional, duration, default: 5s]. Specifies the timeout for rate limiter checks. Defaults to the second unit if unit is not specified.</li>
 * <li><b>mailsPerMinuteDefault</b>: [Optional, long]. Default number of emails a recipient is allowed to receive per minute if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * <li><b>mailsPerHourDefault</b>: [Optional, long]. Default number of emails a recipient is allowed to receive per hour if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * <li><b>mailsPerDayDefault</b>: [Optional, long]. Default number of emails a recipient is allowed to receive per day if no user-specific rate limit exists.
 * If omitted, the value is treated as unlimited. A configured value of <code>-1</code> also means unlimited.</li>
 * </ul>
 *
 * <p>For instance:</p>
 *
 *   <pre><code>
 * &lt;mailet matcher=&quot;All&quot; class=&quot;com.linagora.tmail.mailets.ReceivedRateLimiting&quot;&gt;
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
class ReceivedRateLimiting @Inject()(val rateLimitingRepository: RateLimitingRepository,
                                     val rateLimiterFactory: RateLimiterFactory) extends GenericMailet {

  private var exceededProcessor: String = _
  private var keyPrefix: Option[KeyPrefix] = None
  private var precision: Option[Duration] = None
  private var rateLimiterTimeout: Duration = _
  private var mailsPerMinuteDefault: Option[Long] = None
  private var mailsPerHourDefault: Option[Long] = None
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
    if (!mail.getRecipients.isEmpty) {
      applyRateLimiterPerRecipient(mail)
    }

  private def applyRateLimiterPerRecipient(mail: Mail): Unit = {
    val rateLimitResults: Seq[(MailAddress, RateLimitingResult)] = SFlux.fromIterable(mail.getRecipients.asScala)
      .flatMap(recipient => applyRecipientRateLimit(Username.fromMailAddress(recipient))
        .map(rateLimitingResult => recipient -> rateLimitingResult))
      .collectSeq()
      .block(rateLimiterTimeout.toScala)

    val rateLimitedRecipients: Seq[MailAddress] = rateLimitResults.filter(_._2.equals(RateExceeded)).map(_._1)
    val acceptableRecipients: Seq[MailAddress] = rateLimitResults.filter(_._2.equals(AcceptableRate)).map(_._1)

    (acceptableRecipients, rateLimitedRecipients) match {
      case (acceptable, _) if acceptable.isEmpty => mail.setState(exceededProcessor)
      case (_, exceeded) if exceeded.isEmpty => // do nothing
      case _ =>
        mail.setRecipients(ImmutableList.copyOf(acceptableRecipients.asJava))

        Using(mail.duplicate())(newMail => {
          newMail.setRecipients(ImmutableList.copyOf(rateLimitedRecipients.asJava))
          getMailetContext.sendMail(newMail, exceededProcessor)
        })(LifecycleUtil.dispose(_))
    }
  }

  private def applyRecipientRateLimit(recipient: Username): SMono[RateLimitingResult] =
    SMono.fromPublisher(rateLimitingRepository.getRateLimiting(recipient))
      .map(createRecipientRateLimiters)
      .flatMapMany(SFlux.fromIterable)
      .flatMap(_.rateLimit(recipient))
      .fold[RateLimitingResult](AcceptableRate)((a, b) => a.merge(b))

  private def createRecipientRateLimiters(rateLimitingDefinition: RateLimitingDefinition): Seq[TmailMailRateLimiter] = {
    val mailsReceivedPerMinuteLimit: Option[Long] = rateLimitingDefinition.mailsReceivedPerMinute().toScala
      .map(Long2long)
      .orElse(mailsPerMinuteDefault)
    val mailsReceivedPerHourLimit: Option[Long] = rateLimitingDefinition.mailsReceivedPerHours().toScala
      .map(Long2long)
      .orElse(mailsPerHourDefault)
    val mailsReceivedPerDayLimit: Option[Long] = rateLimitingDefinition.mailsReceivedPerDays().toScala
      .map(Long2long)
      .orElse(mailsPerDayDefault)

    Seq(
      createRateLimiter(rateLimiterFactory, MailsReceivedPerMinuteType, mailsReceivedPerMinuteLimit, precision, keyPrefix),
      createRateLimiter(rateLimiterFactory, MailsReceivedPerHourType, mailsReceivedPerHourLimit, precision, keyPrefix),
      createRateLimiter(rateLimiterFactory, MailsReceivedPerDayType, mailsReceivedPerDayLimit, precision, keyPrefix))
      .flatten
  }
}
