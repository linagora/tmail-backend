package com.linagora.tmail.james.jmap.model

import java.util.UUID

import com.linagora.tmail.james.jmap.method.LabelCreationParseException
import eu.timepit.refined
import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Id, Properties, SetError, UuidState}
import org.apache.james.jmap.mail.Keyword
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.JsObject

object LabelId {
  def fromKeyword(keyword: Keyword): LabelId =
    LabelId(Id.validate(keyword.flagName).toOption.get)

  def generate(): LabelId =
    LabelId(Id.validate(UUID.randomUUID().toString).toOption.get)
}

case class LabelId(id: Id) {
  def toKeyword: Keyword =
    Keyword.of(id.value).get

  def asUnparsedLabelId: UnparsedLabelId =
    UnparsedLabelId(id)

  def serialize: String = id.value
}

object KeywordUtil {
  def generate(): Keyword =
    Keyword.of(UUID.randomUUID().toString).get
}

case class DisplayName(value: String)

object Color {
  private type ColorRegex = MatchesRegex["^#[a-fA-F0-9]{6}$"]

  def validate(string: String): Either[IllegalArgumentException, Color] =
    refined.refineV[ColorRegex](string) match {
      case Left(_) => scala.Left(new IllegalArgumentException(s"The string should be a valid hexadecimal color value following this pattern #[a-fA-F0-9]{6}"))
      case Right(value) => scala.Right(Color(value))
    }
}

case class Color(value: String)

object LabelCreationRequest {
  private val serverSetProperty = Set("id", "keyword")
  private val assignableProperties = Set("displayName", "color")
  private val knownProperties = assignableProperties ++ serverSetProperty

  def validateProperties(jsObject: JsObject): Either[LabelCreationParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
      case (_, unknownProperties) if unknownProperties.nonEmpty =>
        Left(LabelCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some unknown properties were specified"),
          Some(toProperties(unknownProperties.toSet)))))
      case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
        Left(LabelCreationParseException(SetError.invalidArguments(
          SetErrorDescription("Some server-set properties were specified"),
          Some(toProperties(specifiedServerSetProperties.toSet)))))
      case _ => scala.Right(jsObject)
    }

  private def toProperties(strings: Set[String]): Properties = Properties(strings
    .flatMap(string => {
      val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
      refinedValue.fold(_ => None, Some(_))
    }))
}

case class LabelCreationRequest(displayName: DisplayName, color: Option[Color]) {
  def toLabel: Label = {
    val keyword: Keyword = KeywordUtil.generate()

    Label(id = LabelId.fromKeyword(keyword),
      displayName = displayName,
      keyword = keyword,
      color = color)
  }
}

object Label {
  val allProperties: Properties = Properties("id", "displayName", "keyword", "color")
  val idProperty: Properties = Properties("id")
}

case class Label(id: LabelId, displayName: DisplayName, keyword: Keyword, color: Option[Color]) {
  def update(newDisplayName: Option[DisplayName], newColor: Option[Color]): Label =
    copy(displayName = newDisplayName.getOrElse(displayName),
      color = newColor.orElse(color))
}

case class LabelNotFoundException(id: LabelId) extends RuntimeException

case class UnparsedLabelId(id: Id) {
  def asLabelId: LabelId = LabelId(id)
}

case class LabelIds(list: List[UnparsedLabelId])

case class LabelGetRequest(accountId: AccountId,
                           ids: Option[LabelIds],
                           properties: Option[Properties]) extends WithAccountId {
  def validateProperties: Either[IllegalArgumentException, Properties] =
    properties match {
      case None => Right(Label.allProperties)
      case Some(value) =>
        value -- Label.allProperties match {
          case invalidProperties if invalidProperties.isEmpty() => Right(value ++ Label.idProperty)
          case invalidProperties: Properties => Left(new IllegalArgumentException(s"The following properties [${invalidProperties.format()}] do not exist."))
        }
    }
}

object LabelGetResponse {
  def from(accountId: AccountId, state: UuidState, list: Seq[Label], requestIds: Option[LabelIds]): LabelGetResponse =
    requestIds match {
      case None => LabelGetResponse(accountId, state, list)
      case Some(value) =>
        LabelGetResponse(
          accountId = accountId,
          state = state,
          list = list.filter(label => value.list.contains(label.id.asUnparsedLabelId)),
          notFound = value.list.diff(list.map(_.id.asUnparsedLabelId)))
    }
}

case class LabelGetResponse(accountId: AccountId,
                            state: UuidState,
                            list: Seq[Label],
                            notFound: Seq[UnparsedLabelId] = Seq())
