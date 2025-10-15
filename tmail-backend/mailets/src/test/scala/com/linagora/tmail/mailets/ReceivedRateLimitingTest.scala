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

import com.linagora.tmail.mailets.ReceivedRateLimitingTest.{BOB, DOMAIN, RCPT1, RCPT2}
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.{MAILS_RECEIVED_PER_DAYS_UNLIMITED, MAILS_RECEIVED_PER_HOURS_UNLIMITED, MAILS_RECEIVED_PER_MINUTE_UNLIMITED}
import org.apache.james.backends.redis.{DockerRedis, RedisClientFactory, RedisExtension, StandaloneRedisConfiguration}
import org.apache.james.core.{Domain, Username}
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.apache.mailet.Mail
import org.apache.mailet.base.test.{FakeMail, FakeMailContext, FakeMailetConfig}
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

object ReceivedRateLimitingTest {
  val DOMAIN: Domain = Domain.of("domain.tld")
  val BOB: Username = Username.of("bob@domain.tld")
  val ALICE: Username = Username.of("alice@domain.tld")
  val RCPT1: Username = Username.of("recipient1@domain.tld")
  val RCPT2: Username = Username.of("recipient2@domain.tld")
  val RCPT3: Username = Username.of("recipient3@domain.tld")
}

@ExtendWith(Array(classOf[RedisExtension]))
class ReceivedRateLimitingTest {
  var rateLimitingRepository: RateLimitingRepository = _
  var redisRateLimiterFactory: RedisRateLimiterFactory = _
  var receivedRateLimiting: ReceivedRateLimiting = _

  @BeforeEach
  def setup(redis: DockerRedis): Unit = {
    val redisConfiguration: StandaloneRedisConfiguration = StandaloneRedisConfiguration.from(redis.redisURI().toString)
    rateLimitingRepository = new MemoryRateLimitingRepository
    redisRateLimiterFactory = new RedisRateLimiterFactory(redisConfiguration, new RedisClientFactory(FileSystemImpl.forTesting, redisConfiguration))
    receivedRateLimiting = new ReceivedRateLimiting(rateLimitingRepository, redisRateLimiterFactory)
  }

  @Test
  def shouldApplyDefaultLimitsWhenRecipientHasNoLimit(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("mailsPerMinuteDefault", "1")
      .setProperty("mailsPerHourDefault", "10")
      .setProperty("mailsPerDayDefault", "100")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedDefaultLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedDefaultLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedDefaultLimit.getState).isEqualTo("error")
    })
  }

  @Test
  def shouldApplyConfiguredRateLimitsWhenRecipientHasLimit(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(1)
      .mailsReceivedPerHours(10)
      .mailsReceivedPerDays(100)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedConfiguredLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedConfiguredLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedConfiguredLimit.getState).isEqualTo("error")
    })
  }

  @Test
  def shouldApplyConfiguredDomainRateLimitsWhenRecipientHasNoLimit(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(DOMAIN, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(1)
      .mailsReceivedPerHours(10)
      .mailsReceivedPerDays(100)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedConfiguredLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedConfiguredLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedConfiguredLimit.getState).isEqualTo("error")
    })
  }

  @Test
  def shouldApplyConfiguredUserRateLimitsWhenRecipientHasDomainAndUserLimit(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(DOMAIN, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(10)
      .mailsReceivedPerHours(20)
      .mailsReceivedPerDays(30)
      .build())).block()

    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(1)
      .mailsReceivedPerHours(10)
      .mailsReceivedPerDays(20)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedConfiguredLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedConfiguredLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedConfiguredLimit.getState).isEqualTo("error")
    })
  }

  @Test
  def rateExceededMailShouldFlowToConfiguredExceededProcessor(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "10")
      .setProperty("mailsPerHourDefault", "100")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(1)
      .mailsReceivedPerHours(10)
      .mailsReceivedPerDays(100)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedConfiguredLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedConfiguredLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedConfiguredLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def shouldNotImpactMailsWithinRateLimit(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "10")
      .setProperty("mailsPerHourDefault", "100")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(5)
      .mailsReceivedPerHours(50)
      .mailsReceivedPerDays(500)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(mail2)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldNotRateLimitMailsWhenRecipientHasUnlimitedRateLimitConfigured(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1")
      .setProperty("mailsPerHourDefault", "1")
      .setProperty("mailsPerDayDefault", "1")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(MAILS_RECEIVED_PER_MINUTE_UNLIMITED)
      .mailsReceivedPerHours(MAILS_RECEIVED_PER_HOURS_UNLIMITED)
      .mailsReceivedPerDays(MAILS_RECEIVED_PER_DAYS_UNLIMITED)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(mail2)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldNotRateLimitMailsWhenUnlimitedDefaultLimitAndRecipientHasNoConfiguredLimit(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "-1")
      .setProperty("mailsPerHourDefault", "-1")
      .setProperty("mailsPerDayDefault", "-1")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(mail2)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldNotRateLimitMailsWhenNoDefaultLimitAndRecipientHasNoConfiguredLimit(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(mail2)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def configuredMailsPerMinuteLimitShouldWork(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(1)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedMinuteRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedMinuteRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedMinuteRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def defaultMailsPerMinuteLimitShouldWork(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedMinuteRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedMinuteRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedMinuteRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def configuredMailsPerHourLimitShouldWork(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerHours(1)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedHourRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedHourRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedHourRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def defaultMailsPerHourLimitShouldWork(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedHourRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedHourRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedHourRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def configuredMailsPerDayLimitShouldWork(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerDays(1)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedDayRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedDayRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedDayRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def defaultMailsPerDayLimitShouldWork(): Unit = {
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    val shouldExceedDayRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients(RCPT1.asString)
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(shouldExceedDayRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedDayRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def shouldRateLimitPerRecipientAndSplitMail(): Unit = {
    val mailetContext: FakeMailContext = FakeMailContext.defaultContext
    receivedRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .mailetContext(mailetContext)
      .build())

    // RCPT1 has limit of 1 per minute, RCPT2 has no specific limit
    SMono(rateLimitingRepository.setRateLimiting(RCPT1, new RateLimitingDefinition.Builder()
      .mailsReceivedPerMinute(1)
      .build())).block()

    // Mails with both RCPT1 and RCPT2 should be split when RCPT1 exceeded its limit
    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender("sender@domain.tld")
      .recipients(RCPT1.asString())
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender("sender@domain.tld")
      .recipients(RCPT1.asString(), RCPT2.asString())
      .state("transport")
      .build()

    receivedRateLimiting.service(mail1)
    receivedRateLimiting.service(mail2)

    assertSoftly(softly => {
      // Mail1 should pass normally
      assertThat(mail1.getState).isEqualTo("transport")
      assertThat(mail1.getRecipients).containsOnly(RCPT1.asMailAddress())

      // Mail2 should be delivered with RCPT2 only
      assertThat(mail2.getState).isEqualTo("transport")
      assertThat(mail2.getRecipients).containsExactly(RCPT2.asMailAddress())

      // A duplicated mail of mail2 should be sent to the exceeded processor with RCPT1 only
      val sentMails: java.util.List[FakeMailContext.SentMail] = mailetContext.getSentMails
      assertThat(sentMails).hasSize(1)
      softly.assertThat(sentMails.get(0).getState).isEqualTo("tooMuchMails")
      softly.assertThat(sentMails.get(0).getRecipients).containsExactly(RCPT1.asMailAddress())
    })
  }
}
