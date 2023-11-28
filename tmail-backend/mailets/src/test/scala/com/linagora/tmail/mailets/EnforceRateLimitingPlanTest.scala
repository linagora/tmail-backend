package com.linagora.tmail.mailets

import java.time.Duration
import java.util
import java.util.stream.IntStream
import com.codahale.metrics.MetricRegistry
import com.linagora.tmail.mailets.EnforceRateLimitingPlanTest.{USER1, USER2}
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingPlanUserRepository
import com.linagora.tmail.rate.limiter.api.{DeliveryLimitations, InMemoryRateLimitingPlanRepository, LimitTypes, OperationLimitationsType, RateLimitation, RateLimitingPlan, RateLimitingPlanCreateRequest, RateLimitingPlanRepository, RateLimitingPlanUserRepository, RelayLimitations, TransitLimitations}
import eu.timepit.refined.auto._
import org.apache.james.backends.redis.{DockerRedis, RedisConfiguration, RedisExtension}
import org.apache.james.core.Username
import org.apache.james.metrics.api.NoopGaugeRegistry
import org.apache.james.metrics.dropwizard.DropWizardGaugeRegistry
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory
import org.apache.james.util.Size.parse
import org.apache.mailet.base.test.{FakeMail, FakeMailContext, FakeMailetConfig}
import org.apache.mailet.{Mail, MailetConfig}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{BeforeEach, Nested, Test}
import reactor.core.scala.publisher.SMono

object EnforceRateLimitingPlanTest {
  val USER1: Username = Username.of("user1@domain.tld")
  val USER2: Username = Username.of("user2@domain.tld")
}

@ExtendWith(Array(classOf[RedisExtension]))
class EnforceRateLimitingPlanTest {

  var rateLimitationPlanRepository: RateLimitingPlanRepository = _
  var rateLimitingPlanUserRepository: RateLimitingPlanUserRepository = _
  var redisRateLimiterFactory: RedisRateLimiterFactory = _

  def testee(mailetConfig: MailetConfig): EnforceRateLimitingPlan = {
    val mailet: EnforceRateLimitingPlan = new EnforceRateLimitingPlan(rateLimitationPlanRepository, rateLimitingPlanUserRepository, redisRateLimiterFactory,
      new NoopGaugeRegistry())
    mailet.init(mailetConfig)
    mailet
  }

  @BeforeEach
  def setup(redis: DockerRedis): Unit = {
    rateLimitationPlanRepository = new InMemoryRateLimitingPlanRepository
    rateLimitingPlanUserRepository = new MemoryRateLimitingPlanUserRepository
    redisRateLimiterFactory = new RedisRateLimiterFactory(RedisConfiguration.from(redis.redisURI().toString, isCluster = false))

    val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(rateLimitationPlanRepository.create(RateLimitingPlanCreateRequest(
      name = "PaidPlan",
      operationLimitations = OperationLimitationsType.liftOrThrow(Seq(
        TransitLimitations(Seq(
          RateLimitation(name = "transit limit 1",
            period = Duration.ofSeconds(5),
            limits = LimitTypes.from(Map(("count", 1)))))),
        RelayLimitations(Seq(
          RateLimitation(name = "relay limit 1",
            period = Duration.ofSeconds(5),
            limits = LimitTypes.from(Map(("count", 1)))))),
        DeliveryLimitations(Seq(
          RateLimitation(name = "delivery limit 1",
            period = Duration.ofSeconds(5),
            limits = LimitTypes.from(Map(("count", 1)))))))))))
      .block()

    SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(USER1, rateLimitingPlan.id)).block()
    SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(USER2, rateLimitingPlan.id)).block()
  }

  @Nested
  class PerSenderLimit {
    var mailetConfig: FakeMailetConfig.Builder = _
    var mailet: EnforceRateLimitingPlan = _

    @BeforeEach
    def setUpSender(): Unit = {
      mailetConfig = FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .setProperty("operationLimitation", "TransitLimitations")
        .setProperty("precision", "1s")
      mailet = testee(mailetConfig.build())
    }

    @Test
    def shouldFlowToTheIntendedProcessor(): Unit = {
      val mailet: EnforceRateLimitingPlan = testee(mailetConfig
        .setProperty("exceededProcessor", "tooMuchMails")
        .build())

      val mail1: Mail = FakeMail.builder()
        .name("mail1")
        .sender(USER1.asString())
        .recipients("rcpt1@linagora.com")
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender(USER1.asString())
        .recipients("rcpt2@linagora.com")
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("tooMuchMails")
      })
    }

    @Test
    def shouldNotAppliedToRecipientDoesNotInPlan(): Unit = {
      val sender: String = "notInPlan@domain.tld"

      val mail1: Mail = FakeMail.builder()
        .name("mail1")
        .sender(sender)
        .recipients("rcpt1@linagora.com")
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender(sender)
        .recipients("rcpt2@linagora.com")
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("transport")
      })
    }

    @Test
    def shouldBeAppliedPerSender(): Unit = {
      val mail1: Mail = FakeMail.builder()
        .name("mail1")
        .sender(USER1.asString())
        .recipients("rcpt1@linagora.com")
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender(USER2.asString())
        .recipients("rcpt2@linagora.com")
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("transport")
      })
    }

    @Test
    def shouldRateLimitSizeOfEmails(): Unit = {
      val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(rateLimitationPlanRepository.create(RateLimitingPlanCreateRequest(
        name = "LimitPlan",
        operationLimitations = OperationLimitationsType.liftOrThrow(Seq(
          TransitLimitations(Seq(RateLimitation(
            name = "transit limit 1",
            period = Duration.ofSeconds(100),
            limits = LimitTypes.from(Map(("size", parse("100K").asBytes())))))))))))
        .block()

      val username: Username = Username.of("user3@domain.tld")
      SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(username, rateLimitingPlan.id)).block()
      mailet = testee(mailetConfig.build())

      val mail1: Mail = FakeMail.builder()
        .name("mail1")
        .sender(username.asString())
        .recipients("rcpt1@linagora.com")
        .size(50 * 1024)
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender(username.asString())
        .recipients("rcpt1@linagora.com")
        .size(51 * 1024)
        .state("transport")
        .build()

      val mail3: Mail = FakeMail.builder()
        .name("mail3")
        .sender(username.asString())
        .recipients("rcpt1@linagora.com")
        .size(49 * 1024)
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)
      mailet.service(mail3)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("error")
        softly.assertThat(mail3.getState).isEqualTo("transport")
      })
    }

    @Test
    def severalLimitsShouldBeApplied(): Unit = {
      val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(rateLimitationPlanRepository.create(RateLimitingPlanCreateRequest(
        name = "LimitPlan",
        operationLimitations = OperationLimitationsType.liftOrThrow(Seq(
          TransitLimitations(Seq(RateLimitation(
            name = "transit limit 1",
            period = Duration.ofSeconds(100),
            limits = LimitTypes.from(Map(("count", 3), ("size", parse("100K").asBytes())))))))))))
        .block()

      val username: Username = Username.of("user3@domain.tld")
      SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(username, rateLimitingPlan.id)).block()
      mailet = testee(mailetConfig.build())

      val mail1: Mail = FakeMail.builder()
        .name("mail1")
        .sender(username.asString())
        .recipients("rcpt1@linagora.com")
        .size(50 * 1024)
        .state("transport")
        .build()

      // Will exceed the size rate limit
      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender(username.asString())
        .recipients("rcpt2@linagora.com")
        .size(60 * 1024)
        .state("transport")
        .build()

      val mail3: Mail = FakeMail.builder()
        .name("mail4")
        .sender(username.asString())
        .recipients("rcpt3@linagora.com")
        .size(1)
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)
      mailet.service(mail3)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("error")
        softly.assertThat(mail3.getState).isEqualTo("transport")
      })
    }

    @Test
    def shouldNotAppliedWhenOperationLimitationNotFound(): Unit = {
      val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(rateLimitationPlanRepository.create(RateLimitingPlanCreateRequest(
        name = "LimitPlan",
        operationLimitations = OperationLimitationsType.liftOrThrow(Seq(
          DeliveryLimitations(Seq(RateLimitation(
            name = "delivery limit 1",
            period = Duration.ofSeconds(100),
            limits = LimitTypes.from(Map(("count", 1)))))))))))
        .block()

      val username: Username = Username.of("user3@domain.tld")
      SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(username, rateLimitingPlan.id)).block()

      val mail1: Mail = FakeMail.builder()
        .name("mail1")
        .sender(username.asString())
        .recipients("rcpt1@linagora.com")
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender(username.asString())
        .recipients("rcpt2@linagora.com")
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("transport")
      })
    }

    @Test
    def relayLimitationsShouldRateLimitPerSender(): Unit = {
      val mailet: EnforceRateLimitingPlan = testee(FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .setProperty("operationLimitation", "RelayLimitations")
        .setProperty("precision", "1s")
        .build())

      val mail1: Mail = FakeMail.builder()
        .name("mail1")
        .sender(USER1.asString())
        .recipients("rcpt1@linagora.com")
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender(USER1.asString())
        .recipients("rcpt2@linagora.com")
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("error")
      })
    }
  }

  @Nested
  class PerRecipientLimit {
    var mailetConfig: FakeMailetConfig.Builder = _
    var mailet: EnforceRateLimitingPlan = _

    @BeforeEach
    def setUpSender(): Unit = {
      mailetConfig = FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .setProperty("operationLimitation", "DeliveryLimitations")
        .setProperty("precision", "1s")
      mailet = testee(mailetConfig.build())
    }

    @Test
    def shouldFlowToTheIntendedProcessor(): Unit = {
      val mailet: EnforceRateLimitingPlan = testee(mailetConfig
        .setProperty("exceededProcessor", "tooMuchMails")
        .build())

      val mail1: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(USER1.asString())
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender("sender1@domain.tld")
        .recipients(USER1.asString())
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("tooMuchMails")
      })
    }

    @Test
    def shouldNotAppliedToRecipientDoesNotInPlan(): Unit = {
      val recipient: String = "notInPlan@domain.tld"

      val mail1: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(recipient)
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender("sender1@domain.tld")
        .recipients(recipient)
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("transport")
      })
    }

    @Test
    def shouldBeAppliedPerRecipient(): Unit = {
      val mailet: EnforceRateLimitingPlan = testee(mailetConfig.build())

      val mail1: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(USER1.asString())
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender("sender1@domain.tld")
        .recipients(USER2.asString())
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("transport")
      })
    }

    @Test
    def shouldNotBeAppliedWhenDoNotHaveRecipient(): Unit = {
      val mail: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .state("transport")
        .build()

      mailet.service(mail)
      assertThat(mail.getState).isEqualTo("transport")
    }

    @Test
    def shouldNotBeAppliedPerSender(): Unit = {
      val mail1: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(USER1.asString())
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender("sender2@domain.tld")
        .recipients(USER1.asString())
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("error")
      })
    }

    @Test
    def shouldRateLimitSizeOfEmails(): Unit = {
      val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(rateLimitationPlanRepository.create(RateLimitingPlanCreateRequest(
        name = "LimitPlan",
        operationLimitations = OperationLimitationsType.liftOrThrow(Seq(
          DeliveryLimitations(Seq(RateLimitation(
            name = "transit limit 1",
            period = Duration.ofSeconds(100),
            limits = LimitTypes.from(Map(("size", parse("100K").asBytes())))))))))))
        .block()

      val username: Username = Username.of("user3@domain.tld")
      SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(username, rateLimitingPlan.id)).block()

      val mail1: Mail = FakeMail.builder()
        .name("mail1")
        .sender("sender@domain.tld")
        .recipients(username.asString())
        .size(50 * 1024)
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender("sender@domain.tld")
        .recipients(username.asString())
        .size(51 * 1024)
        .state("transport")
        .build()

      val mail3: Mail = FakeMail.builder()
        .name("mail3")
        .sender("sender@domain.tld")
        .recipients(username.asString())
        .size(49 * 1024)
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)
      mailet.service(mail3)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("error")
        softly.assertThat(mail3.getState).isEqualTo("transport")
      })
    }

    @Test
    def shouldRateLimitPerRecipient(): Unit = {
      val mailetContext: FakeMailContext = FakeMailContext.defaultContext
      val mailet: EnforceRateLimitingPlan = testee(mailetConfig
        .mailetContext(mailetContext).build())

      val mail1: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(USER1.asString())
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(USER1.asString(), USER2.asString())
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("transport")
      })

      val sentMails: util.List[FakeMailContext.SentMail] = mailetContext.getSentMails
      assertThat(sentMails).hasSize(1)
      assertSoftly(softly => {
        softly.assertThat(sentMails.get(0).getState).isEqualTo("error")
        softly.assertThat(sentMails.get(0).getRecipients).containsExactlyInAnyOrder(USER1.asMailAddress())
      })
    }

    @Test
    def shouldRateLimitedWhenAllRecipientsExceeded(): Unit = {
      val mail1: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(USER1.asString(), USER2.asString())
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(USER1.asString(), USER2.asString())
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("error")
      })
    }

    @Test
    def shouldNotAppliedWhenOperationLimitationNotFound(): Unit = {
      val rateLimitingPlan: RateLimitingPlan = SMono.fromPublisher(rateLimitationPlanRepository.create(RateLimitingPlanCreateRequest(
        name = "LimitPlan",
        operationLimitations = OperationLimitationsType.liftOrThrow(Seq(
          TransitLimitations(Seq(RateLimitation(
            name = "transit limit 1",
            period = Duration.ofSeconds(100),
            limits = LimitTypes.from(Map(("count", 1)))))))))))
        .block()

      val username: Username = Username.of("user3@domain.tld")
      SMono.fromPublisher(rateLimitingPlanUserRepository.applyPlan(username, rateLimitingPlan.id)).block()

      val mail1: Mail = FakeMail.builder()
        .name("mail")
        .sender("sender1@domain.tld")
        .recipients(username.asString())
        .state("transport")
        .build()

      val mail2: Mail = FakeMail.builder()
        .name("mail2")
        .sender("sender1@domain.tld")
        .recipients(username.asString())
        .state("transport")
        .build()

      mailet.service(mail1)
      mailet.service(mail2)

      assertSoftly(softly => {
        softly.assertThat(mail1.getState).isEqualTo("transport")
        softly.assertThat(mail2.getState).isEqualTo("transport")
      })
    }
  }

  @Nested
  class Configuration {
    @Test
    def shouldFailWhenNoOperationLimitation(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldFailWhenInvalidOperationLimitation(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .setProperty("operationLimitation", "badOperation")
        .build()))
        .isInstanceOf(classOf[IllegalArgumentException])
    }

    @Test
    def shouldNotFailWhenValidOperationLimitation(): Unit = {
      assertThatCode(() => testee(FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .setProperty("operationLimitation", "TransitLimitations")
        .build()))
        .doesNotThrowAnyException()
    }

    @Test
    def cacheExpirationShouldSupportUnits(): Unit = {
      assertThat(testee(FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .setProperty("operationLimitation", "TransitLimitations")
        .setProperty("cacheExpiration", "2h")
        .build()).parseCacheExpiration())
        .isEqualTo(Some(Duration.ofHours(2)))
    }


    @Test
    def cacheExpirationWithNoUnitShouldDefaultToSeconds(): Unit = {
      assertThat(testee(FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .setProperty("operationLimitation", "TransitLimitations")
        .setProperty("cacheExpiration", "2")
        .build()).parseCacheExpiration())
        .isEqualTo(Some(Duration.ofSeconds(2)))
    }

    @Test
    def shouldFailWhenBadCacheExpiration(): Unit = {
      assertThatThrownBy(() => testee(FakeMailetConfig.builder()
        .mailetName("EnforceRateLimitingPlan")
        .setProperty("operationLimitation", "TransitLimitations")
        .setProperty("cacheExpiration", "bad")
        .build()).parseCacheExpiration())
        .isInstanceOf(classOf[IllegalArgumentException])
    }
  }

  @Test
  def planShouldBeCachedWhenConfigurationIsProvided(): Unit = {
    val metricRegistry: MetricRegistry = new MetricRegistry()
    val gaugeRegistry: DropWizardGaugeRegistry = new DropWizardGaugeRegistry(metricRegistry)

    val mailet: EnforceRateLimitingPlan = new EnforceRateLimitingPlan(rateLimitationPlanRepository, rateLimitingPlanUserRepository, redisRateLimiterFactory,
      gaugeRegistry)

    mailet.init(FakeMailetConfig.builder()
      .mailetName("EnforceRateLimitingPlan")
      .setProperty("operationLimitation", "TransitLimitations")
      .setProperty("precision", "1s")
      .setProperty("cacheExpiration", "2m")
      .build())

    val sentCount: Int = 10;
    IntStream.range(0, sentCount)
      .forEach(index => {
        val mail: Mail = FakeMail.builder()
          .name("mail" + index)
          .sender(USER1.asString())
          .recipients("rcpt1@linagora.com")
          .state("transport")
          .build()
        mailet.service(mail)
      })
    assertThat(metricRegistry.getGauges.get("TransitLimitations.rate_limiting_plan.cache.get.hitCount").getValue)
      .isEqualTo(java.lang.Long.valueOf(sentCount - 1))
  }
}
