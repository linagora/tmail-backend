package com.linagora.tmail.mailets

import java.time.Duration
import java.time.temporal.ChronoUnit

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.linagora.tmail.mailets.EnforceRateLimitingPlan.{ACCEPTABLE_OPERATIONS, LIMIT_PER_RECIPIENTS_OPERATIONS, LIMIT_PER_SENDER_OPERATIONS}
import com.linagora.tmail.rate.limiter.api.OperationLimitations.{DELIVERY_LIMITATIONS_NAME, RELAY_LIMITATIONS_NAME, TRANSIT_LIMITATIONS_NAME}
import com.linagora.tmail.rate.limiter.api.{CacheRateLimitingPlan, OperationLimitations, RateLimitingPlanId, RateLimitingPlanNotFoundException, RateLimitingPlanRepository, RateLimitingPlanUserRepository}
import javax.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.lifecycle.api.LifecycleUtil
import org.apache.james.metrics.api.GaugeRegistry
import org.apache.james.rate.limiter.api.{AcceptableRate, RateExceeded, RateLimiterFactory, RateLimitingResult, Rule, Rules}
import org.apache.james.transport.mailets.ConfigurationOps.DurationOps
import org.apache.james.transport.mailets.KeyPrefix
import org.apache.james.util.DurationParser
import org.apache.mailet.Mail
import org.apache.mailet.base.GenericMailet
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.util.Using

object EnforceRateLimitingPlan {
  val LIMIT_PER_SENDER_OPERATIONS: Set[String] = Set(TRANSIT_LIMITATIONS_NAME, RELAY_LIMITATIONS_NAME)
  val LIMIT_PER_RECIPIENTS_OPERATIONS: Set[String] = Set(DELIVERY_LIMITATIONS_NAME)
  val ACCEPTABLE_OPERATIONS: Set[String] = LIMIT_PER_SENDER_OPERATIONS ++ LIMIT_PER_RECIPIENTS_OPERATIONS
}

class EnforceRateLimitingPlan @Inject()(planRepository: RateLimitingPlanRepository,
                                        planUserRepository: RateLimitingPlanUserRepository,
                                        rateLimiterFactory: RateLimiterFactory,
                                        gaugeRegistry: GaugeRegistry) extends GenericMailet {

  private var operationLimitation: String = _
  private var exceededProcessor: String = _
  private var rateLimitSender: Boolean = _
  private var rateLimitRecipient: Boolean = _
  private var planRateLimiterResolver: PlanRateLimiterResolver = _
  private var planStore: RateLimitingPlanRepository = _

  override def init(): Unit = {
    exceededProcessor = getInitParameter("exceededProcessor", Mail.ERROR)
    operationLimitation = getInitParameter("operationLimitation")
    rateLimitSender = LIMIT_PER_SENDER_OPERATIONS.contains(operationLimitation)
    rateLimitRecipient = LIMIT_PER_RECIPIENTS_OPERATIONS.contains(operationLimitation)
    Preconditions.checkArgument(operationLimitation != null && operationLimitation.nonEmpty, "`operationLimitation` is compulsory".asInstanceOf[Object])
    Preconditions.checkArgument(ACCEPTABLE_OPERATIONS.contains(operationLimitation), s"`operationLimitation` must be [${String.join(", ", ACCEPTABLE_OPERATIONS.asJava)}]".asInstanceOf[Object])

    planStore = parseCacheExpiration()
      .map(duration => new CacheRateLimitingPlan(planRepository, duration, gaugeRegistry, Some(operationLimitation)))
      .getOrElse(planRepository)

    planRateLimiterResolver = PlanRateLimiterResolver(
      rateLimiterFactory = rateLimiterFactory,
      keyPrefix = Option(getInitParameter("keyPrefix")).map(KeyPrefix),
      precision = getMailetConfig.getDuration("duration"))
  }

  @VisibleForTesting
  def parseCacheExpiration(): Option[Duration] = Option(getInitParameter("cacheExpiration"))
    .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
    .map(duration => {
      Preconditions.checkArgument(!duration.isZero && !duration.isNegative, "`cacheExpiration` must be positive".asInstanceOf[Object])
      duration
    })

  override def service(mail: Mail): Unit =
    if (rateLimitSender) {
      mail.getMaybeSender.asOptional()
        .ifPresent(sender => applyRateLimiterPerSender(mail, Username.fromMailAddress(sender)))
    } else if (rateLimitRecipient && !mail.getRecipients.isEmpty) {
      applyRateLimiterPerRecipient(mail)
    }

  private def applyRateLimiter(mail: Mail, username: Username): SMono[RateLimitingResult] =
    SMono.fromPublisher(planUserRepository.getPlanByUser(username))
      .flatMap(retrieveRateLimiter)
      .flatMapMany(SFlux.fromIterable)
      .flatMap(_.rateLimit(username, mail))
      .fold[RateLimitingResult](AcceptableRate)((a, b) => a.merge(b))
      .onErrorResume {
        case _: RateLimitingPlanNotFoundException => SMono.just[RateLimitingResult](AcceptableRate)
      }

  private def retrieveRateLimiter(id: RateLimitingPlanId): SMono[Seq[TmailPlanRateLimiter]] =
    SMono.fromPublisher(planStore.get(id))
      .flatMapIterable(_.operationLimitations)
      .filter(_.asString().equals(operationLimitation))
      .next()
      .map(planRateLimiterResolver.extractRateLimiters(id, _))

  private def applyRateLimiterPerSender(mail: Mail, username: Username): Unit = {
    val rateLimitingResult: RateLimitingResult = applyRateLimiter(mail, username).block()
    if (rateLimitingResult.equals(RateExceeded)) {
      mail.setState(exceededProcessor)
    }
  }

  private def applyRateLimiterPerRecipient(mail: Mail): Unit = {
    val rateLimitResults: Seq[(MailAddress, RateLimitingResult)] = SFlux.fromIterable(mail.getRecipients.asScala)
      .flatMap(recipient => applyRateLimiter(mail, Username.fromMailAddress(recipient))
        .map(rateLimitingResult => recipient -> rateLimitingResult))
      .collectSeq()
      .block()

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
}

case class PlanRateLimiterResolver(rateLimiterFactory: RateLimiterFactory,
                                   keyPrefix: Option[KeyPrefix],
                                   precision: Option[Duration]) {

  def extractRateLimiters(planId: RateLimitingPlanId, operationLimitations: OperationLimitations): Seq[TmailPlanRateLimiter] =
    aggregateRule(operationLimitations)
      .map(pair => TmailPlanRateLimiter(
        rateLimiter = rateLimiterFactory.withSpecification(pair._2, precision),
        limitTypeName = pair._1,
        keyPrefix = keyPrefix,
        planId = planId,
        operationLimitationName = operationLimitations.asString()))
      .toSeq

  private def aggregateRule(operationLimitations: OperationLimitations): Map[String, Rules] =
    operationLimitations.rateLimitations()
      .flatMap(rateLimitation => rateLimitation.limits.value
        .map(limit => limit -> rateLimitation.period))
      .groupBy(_._1.asString())
      .map(pair => pair._1 -> Rules(pair._2.map(pair => Rule(pair._1.allowedQuantity(), pair._2))))

}
