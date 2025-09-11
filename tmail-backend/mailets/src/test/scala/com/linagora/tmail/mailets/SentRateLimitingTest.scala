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

import com.linagora.tmail.mailets.SentRateLimitingTest.{ALICE, BOB}
import com.linagora.tmail.rate.limiter.api.RateLimitingRepository
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingRepository
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition
import com.linagora.tmail.rate.limiter.api.model.RateLimitingDefinition.{MAILS_SENT_PER_DAYS_UNLIMITED, MAILS_SENT_PER_HOURS_UNLIMITED, MAILS_SENT_PER_MINUTE_UNLIMITED}
import org.apache.james.backends.redis.{DockerRedis, RedisClientFactory, RedisExtension, StandaloneRedisConfiguration}
import org.apache.james.core.Username
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.apache.mailet.Mail
import org.apache.mailet.base.test.{FakeMail, FakeMailetConfig}
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

object SentRateLimitingTest {
  val BOB: Username = Username.of("bob@domain.tld")
  val ALICE: Username = Username.of("alice@domain.tld")
}

@ExtendWith(Array(classOf[RedisExtension]))
class SentRateLimitingTest {
  var rateLimitingRepository: RateLimitingRepository = _
  var redisRateLimiterFactory: RedisRateLimiterFactory = _
  var sentRateLimiting: SentRateLimiting = _

  @BeforeEach
  def setup(redis: DockerRedis): Unit = {
    val redisConfiguration: StandaloneRedisConfiguration = StandaloneRedisConfiguration.from(redis.redisURI().toString)
    rateLimitingRepository = new MemoryRateLimitingRepository
    redisRateLimiterFactory = new RedisRateLimiterFactory(redisConfiguration, new RedisClientFactory(FileSystemImpl.forTesting, redisConfiguration))
    sentRateLimiting = new SentRateLimiting(rateLimitingRepository, redisRateLimiterFactory)
  }

  @Test
  def shouldApplyDefaultLimitsWhenSenderHasNoLimit(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("mailsPerMinuteDefault", "1")
      .setProperty("mailsPerHourDefault", "10")
      .setProperty("mailsPerDayDefault", "100")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedDefaultLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedDefaultLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedDefaultLimit.getState).isEqualTo("error")
    })
  }

  @Test
  def shouldApplyConfiguredRateLimitsWhenSenderHasLimit(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(BOB, new RateLimitingDefinition.Builder()
      .mailsSentPerMinute(1)
      .mailsSentPerHours(10)
      .mailsSentPerDays(100)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedConfiguredLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedConfiguredLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedConfiguredLimit.getState).isEqualTo("error")
    })
  }

  @Test
  def rateExceededMailShouldFlowToConfiguredExceededProcessor(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "10")
      .setProperty("mailsPerHourDefault", "100")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(BOB, new RateLimitingDefinition.Builder()
      .mailsSentPerMinute(1)
      .mailsSentPerHours(10)
      .mailsSentPerDays(100)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedConfiguredLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedConfiguredLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedConfiguredLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def shouldNotImpactMailsWithinRateLimit(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "10")
      .setProperty("mailsPerHourDefault", "100")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(BOB, new RateLimitingDefinition.Builder()
      .mailsSentPerMinute(5)
      .mailsSentPerHours(50)
      .mailsSentPerDays(500)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(mail2)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldNotRateLimitMailsWhenSenderHasUnlimitedRateLimitConfigured(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1")
      .setProperty("mailsPerHourDefault", "1")
      .setProperty("mailsPerDayDefault", "1")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(BOB, new RateLimitingDefinition.Builder()
      .mailsSentPerMinute(MAILS_SENT_PER_MINUTE_UNLIMITED)
      .mailsSentPerHours(MAILS_SENT_PER_HOURS_UNLIMITED)
      .mailsSentPerDays(MAILS_SENT_PER_DAYS_UNLIMITED)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(mail2)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldNotRateLimitMailsWhenUnlimitedDefaultLimitAndSenderHasNoConfiguredLimit(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "-1")
      .setProperty("mailsPerHourDefault", "-1")
      .setProperty("mailsPerDayDefault", "-1")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(mail2)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def shouldNotRateLimitMailsWhenNoDefaultLimitAndSenderHasNoConfiguredLimit(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val mail2: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(mail2)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(mail2.getState).isEqualTo("transport")
    })
  }

  @Test
  def configuredMailsPerMinuteLimitShouldWork(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(BOB, new RateLimitingDefinition.Builder()
      .mailsSentPerMinute(1)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedMinuteRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedMinuteRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedMinuteRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def defaultMailsPerMinuteLimitShouldWork(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedDefaultMinuteRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedDefaultMinuteRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedDefaultMinuteRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def configuredMailsPerHourLimitShouldWork(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(BOB, new RateLimitingDefinition.Builder()
      .mailsSentPerHours(1)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedHourRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedHourRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedHourRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def defaultMailsPerHourLimitShouldWork(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedDefaultHourRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedDefaultHourRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedDefaultHourRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def configuredMailsPerDayLimitShouldWork(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    SMono(rateLimitingRepository.setRateLimiting(BOB, new RateLimitingDefinition.Builder()
      .mailsSentPerDays(1)
      .build())).block()

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedDayRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedDayRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedDayRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def defaultMailsPerDayLimitShouldWork(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "1000")
      .setProperty("mailsPerHourDefault", "1000")
      .setProperty("mailsPerDayDefault", "1")
      .build())

    val mail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()

    val shouldExceedDefaultDayRateLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()

    sentRateLimiting.service(mail1)
    sentRateLimiting.service(shouldExceedDefaultDayRateLimit)

    assertSoftly(softly => {
      softly.assertThat(mail1.getState).isEqualTo("transport")
      softly.assertThat(shouldExceedDefaultDayRateLimit.getState).isEqualTo("tooMuchMails")
    })
  }

  @Test
  def shouldRateLimitPerSender(): Unit = {
    sentRateLimiting.init(FakeMailetConfig.builder()
      .setProperty("precision", "1s")
      .setProperty("exceededProcessor", "tooMuchMails")
      .setProperty("mailsPerMinuteDefault", "10")
      .setProperty("mailsPerHourDefault", "100")
      .setProperty("mailsPerDayDefault", "1000")
      .build())

    // GIVEN Bob has limit configured, and Alice has no limit configured
    SMono(rateLimitingRepository.setRateLimiting(BOB, new RateLimitingDefinition.Builder()
      .mailsSentPerMinute(1)
      .build())).block()

    // Bob sends 2 mails, second should be rate limited
    val bobSentMail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(BOB.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()
    val bobSentMail2ShouldExceedLimit: Mail = FakeMail.builder()
      .name("mail2")
      .sender(BOB.asString())
      .recipients("rcpt2@linagora.com")
      .state("transport")
      .build()
    sentRateLimiting.service(bobSentMail1)
    sentRateLimiting.service(bobSentMail2ShouldExceedLimit)

    // Alice sends 1 mail, should not be rate limited
    val aliceSentMail1: Mail = FakeMail.builder()
      .name("mail1")
      .sender(ALICE.asString())
      .recipients("rcpt1@linagora.com")
      .state("transport")
      .build()
    sentRateLimiting.service(aliceSentMail1)

    assertSoftly(softly => {
      softly.assertThat(bobSentMail1.getState).isEqualTo("transport")
      softly.assertThat(bobSentMail2ShouldExceedLimit.getState).isEqualTo("tooMuchMails")
      // Bob rate limited should not impact Alice
      softly.assertThat(aliceSentMail1.getState).isEqualTo("transport")
    })
  }
}
