package com.linagora.tmail.webadmin

import java.time.Duration
import java.util.Optional

import com.linagora.tmail.rate.limiter.api.LimitTypes.LimitTypes
import com.linagora.tmail.rate.limiter.api.OperationLimitations.{DELIVERY_LIMITATIONS_NAME, RELAY_LIMITATIONS_NAME, TRANSIT_LIMITATIONS_NAME}
import com.linagora.tmail.rate.limiter.api.{Count, LimitTypes, OperationLimitations, OperationLimitationsType, RateLimitation, RateLimitingPlanRepository, RateLimitingPlan, RateLimitingPlanCreateRequest, RateLimitingPlanId, RateLimitingPlanName, RateLimitingPlanNotFoundException, RateLimitingPlanResetRequest, Size}
import com.linagora.tmail.webadmin.model.RateLimitingPlanCreateRequestDTO.{DELIVERY_LIMIT_KEY, RELAY_LIMIT_KEY, TRANSIT_LIMIT_KEY}
import com.linagora.tmail.webadmin.model.{GetAllRateLimitPlanResponseDTO, OperationLimitationsDTO, RateLimitationDTO, RateLimitingPlanCreateRequestDTO, RateLimitingPlanDTO, RateLimitingPlanIdResponse, RateLimitingPlanResetRequestDTO}
import javax.inject.Inject
import org.apache.james.rate.limiter.api.AllowedQuantity
import org.apache.james.webadmin.Constants.SEPARATOR
import org.apache.james.webadmin.Routes
import org.apache.james.webadmin.utils.{ErrorResponder, JsonExtractor, JsonTransformer, Responses}
import org.eclipse.jetty.http.HttpStatus.{CREATED_201, NOT_FOUND_404}
import reactor.core.scala.publisher.{SFlux, SMono}
import spark.{Request, Response, Route, Service}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.jdk.StreamConverters._

class RateLimitPlanManagementRoutes @Inject()(planRepository: RateLimitingPlanRepository,
                                              jsonTransformer: JsonTransformer) extends Routes {
  private val PLAN_ID_PARAM = ":planId"
  private val PLAN_NAME_PARAM = ":planName"
  private val BASE_PATH = s"${SEPARATOR}rate-limit-plans"
  private val CREATE_A_PLAN_PATH = s"$BASE_PATH$SEPARATOR$PLAN_NAME_PARAM"
  private val UPDATE_A_PLAN_PATH = s"$BASE_PATH$SEPARATOR$PLAN_ID_PARAM"
  private val GET_A_PLAN_PATH = s"$BASE_PATH$SEPARATOR$PLAN_ID_PARAM"
  private val GET_ALL_PLAN_PATH = BASE_PATH

  val createRequestExtractor = new JsonExtractor[RateLimitingPlanCreateRequestDTO](classOf[RateLimitingPlanCreateRequestDTO])
  val resetRequestExtractor = new JsonExtractor[RateLimitingPlanResetRequestDTO](classOf[RateLimitingPlanResetRequestDTO])

  override def getBasePath: String = BASE_PATH

  override def define(service: Service): Unit = {
    service.post(CREATE_A_PLAN_PATH, createAPlan, jsonTransformer)
    service.put(UPDATE_A_PLAN_PATH, updateAPlan, jsonTransformer)
    service.get(GET_A_PLAN_PATH, getAPlan, jsonTransformer)
    service.get(GET_ALL_PLAN_PATH, getAllPlan, jsonTransformer)
  }

  private def createAPlan: Route = (request: Request, response: Response) =>
    SMono.fromPublisher(planRepository.create(toCreateRequest(request)))
      .map(plan => {
        response.status(CREATED_201)
        new RateLimitingPlanIdResponse(plan.id.serialize())
      })
      .block()

  private def updateAPlan: Route = (request: Request, response: Response) =>
    SMono.fromPublisher(planRepository.update(toResetRequest(request)))
      .onErrorResume { case e: RateLimitingPlanNotFoundException => SMono.error(
        ErrorResponder.builder()
          .statusCode(NOT_FOUND_404)
          .`type`(ErrorResponder.ErrorType.NOT_FOUND)
          .message("Plan does not exist")
          .haltError())
      }
      .`then`(SMono.just(Responses.returnNoContent(response)))
      .block()

  private def getAPlan: Route = (request: Request, response: Response) =>
    SMono.fromPublisher(planRepository.get(extractRateLimitingPlanId(request)))
      .map(toRateLimitingPlanDTO)
      .onErrorResume { case e: RateLimitingPlanNotFoundException => SMono.error(
        ErrorResponder.builder()
          .statusCode(NOT_FOUND_404)
          .`type`(ErrorResponder.ErrorType.NOT_FOUND)
          .message("Plan does not exist")
          .haltError())
      }
      .block()

  private def getAllPlan: Route = (request: Request, response: Response) =>
    SFlux.fromPublisher(planRepository.list())
      .map(toRateLimitingPlanDTO)
      .collectSeq()
      .map(seq => new GetAllRateLimitPlanResponseDTO(seq.toList.asJava))
      .block()

  private def toRateLimitingPlanDTO(rateLimitingPlan: RateLimitingPlan): RateLimitingPlanDTO =
    new RateLimitingPlanDTO(rateLimitingPlan.id.serialize(),
      rateLimitingPlan.name.value,
      toOperationLimitationsDTO(rateLimitingPlan.operationLimitations, OperationLimitations.TRANSIT_LIMITATIONS_NAME),
      toOperationLimitationsDTO(rateLimitingPlan.operationLimitations, OperationLimitations.RELAY_LIMITATIONS_NAME),
      toOperationLimitationsDTO(rateLimitingPlan.operationLimitations, OperationLimitations.DELIVERY_LIMITATIONS_NAME))

  private def toOperationLimitationsDTO(operationLimitation: Seq[OperationLimitations], operationType: String): Optional[OperationLimitationsDTO] =
    operationLimitation.find(_.asString().equals(operationType))
      .map(transitLimitation => new OperationLimitationsDTO(transitLimitation.rateLimitations().map(toRateLimitationDTO).toList.asJava))
      .toJava

  private def toRateLimitationDTO(rateLimitation: RateLimitation): RateLimitationDTO =
    new RateLimitationDTO(rateLimitation.name, rateLimitation.period.getSeconds,
      extractLimitType(rateLimitation, LimitTypes.COUNT), extractLimitType(rateLimitation, LimitTypes.SIZE))

  private def extractLimitType(rateLimitation: RateLimitation, typeString: String): Long =
    rateLimitation.limits.value
      .filter(_.asString().equals(typeString))
      .head
      .allowedQuantity()
      .value

  private def toCreateRequest(request: Request): RateLimitingPlanCreateRequest = {
    val createRequestDTO = createRequestExtractor.parse(request.body())
    val combinedLimitations = java.util.stream.Stream.of(
      createRequestDTO.getOrDefault(TRANSIT_LIMIT_KEY, Optional.empty()).map(toOperationLimitation(TRANSIT_LIMITATIONS_NAME, _)),
      createRequestDTO.getOrDefault(DELIVERY_LIMIT_KEY, Optional.empty()).map(toOperationLimitation(DELIVERY_LIMITATIONS_NAME, _)),
      createRequestDTO.getOrDefault(RELAY_LIMIT_KEY, Optional.empty()).map(toOperationLimitation(RELAY_LIMITATIONS_NAME, _)))
      .filter(_.isPresent)
      .map(_.get)
      .toScala(Seq)

    RateLimitingPlanCreateRequest(extractRateLimitingPlanName(request), OperationLimitationsType.liftOrThrow(combinedLimitations))
  }

  private def toResetRequest(request: Request): RateLimitingPlanResetRequest = {
    val resetRequestDTO = resetRequestExtractor.parse(request.body())
    val combinedLimitations = java.util.stream.Stream.of(
      resetRequestDTO.getTransitLimits.map(toOperationLimitation(TRANSIT_LIMITATIONS_NAME, _)),
      resetRequestDTO.getDeliveryLimits.map(toOperationLimitation(DELIVERY_LIMITATIONS_NAME, _)),
      resetRequestDTO.getRelayLimits.map(toOperationLimitation(RELAY_LIMITATIONS_NAME, _)))
      .filter(_.isPresent)
      .map(_.get)
      .toScala(Seq)

    RateLimitingPlanResetRequest(extractRateLimitingPlanId(request), RateLimitingPlanName.liftOrThrow(resetRequestDTO.getPlanName),
      OperationLimitationsType.liftOrThrow(combinedLimitations))
  }

  private def toOperationLimitation(operationName: String, dto: OperationLimitationsDTO): OperationLimitations =
    OperationLimitations.liftOrThrow(operationName, dto.getRateLimitationDTOList
      .stream()
      .map(rateLimitationDTO => toRateLimitation(rateLimitationDTO))
      .toScala(Seq))

  private def toRateLimitation(dto: RateLimitationDTO): RateLimitation = {
    require(dto.getPeriodInSeconds.>=(0), "Rate limitation period must not be negative")
    RateLimitation(name = dto.getName, period = Duration.ofSeconds(dto.getPeriodInSeconds), limits = toLimitTypes(dto.getCount, dto.getSize))
  }

  private def toLimitTypes(count: Long, size: Long): LimitTypes = {
    val countType: Count = Count(AllowedQuantity.liftOrThrow(count))
    val sizeType: Size = Size(AllowedQuantity.liftOrThrow(size))
    LimitTypes.liftOrThrow(Set(countType, sizeType))
  }

  private def extractRateLimitingPlanId(request: Request) = RateLimitingPlanId.parse(request.params(PLAN_ID_PARAM))

  private def extractRateLimitingPlanName(request: Request) = RateLimitingPlanName.liftOrThrow(request.params(PLAN_NAME_PARAM))
}
