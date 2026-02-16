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

package com.linagora.tmail.james.jmap.settings

import java.time.Period
import java.util.Locale

import com.google.common.base.CharMatcher
import com.linagora.tmail.james.jmap.settings.InboxArchivalFormat.InboxArchivalFormat
import com.linagora.tmail.james.jmap.settings.JmapSettings.{AI_LABEL_CATEGORIZATION_DISABLE_DEFAULT_VALUE, AI_LABEL_CATEGORIZATION_ENABLE_KEY, AI_NEEDS_ACTION_ENABLE_KEY, AI_RAG_DISABLE_DEFAULT_VALUE, AI_RAG_ENABLE_KEY, INBOX_ARCHIVAL_ENABLE_DEFAULT_VALUE, INBOX_ARCHIVAL_ENABLE_KEY, INBOX_ARCHIVAL_FORMAT_DEFAULT_VALUE, INBOX_ARCHIVAL_FORMAT_KEY, INBOX_ARCHIVAL_PERIOD_DEFAULT_VALUE, INBOX_ARCHIVAL_PERIOD_KEY, LANGUAGE_KEY, cleanupDefaultPeriod, spamCleanupEnabledSetting, spamCleanupPeriodSetting, trashCleanupEnabledSetting, trashCleanupPeriodSetting}
import com.linagora.tmail.james.jmap.settings.JmapSettingsKey.SettingKeyType
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}
import org.apache.james.jmap.api.model.{State, TypeName}
import org.apache.james.jmap.core.UuidState

import scala.util.Try

object JmapSettingsKey {
  case class JmapSettingKeyConstraint()

  type SettingKeyType = String Refined JmapSettingKeyConstraint

  private val charMatcher: CharMatcher = CharMatcher.inRange('a', 'z')
    .or(CharMatcher.inRange('0', '9'))
    .or(CharMatcher.inRange('A', 'Z'))
    .or(CharMatcher.is('.'))
    .or(CharMatcher.is('_'))
    .or(CharMatcher.is('-'))
    .or(CharMatcher.is('#'))

  def validateSettingKey(string: String): Either[IllegalArgumentException, SettingKeyType] =
    refined.refineV[JmapSettingKeyConstraint](string)(
        Validate.fromPredicate(s => s.nonEmpty && s.length < 256 && charMatcher.matchesAllOf(s),
          s => s"'$s' contains some invalid characters. Should be [#a-zA-Z0-9-_#.] and no longer than 255 chars",
          JmapSettingKeyConstraint()))
      .left.map(new IllegalArgumentException(_))

  def liftOrThrow(value: String): JmapSettingsKey =
    validateSettingKey(value) match {
      case Left(error) => throw new IllegalArgumentException(error)
      case Right(settingKey) => JmapSettingsKey(settingKey)
    }

  def validate(value: String): Either[IllegalArgumentException, JmapSettingsKey] =
    validateSettingKey(value) match {
      case Left(error) => Left(error)
      case Right(settingKey) => Right(JmapSettingsKey(settingKey))
    }
}

case class JmapSettingsKey(value: SettingKeyType) {
  def asString(): String = value.value
}

case class JmapSettingsValue(value: String) extends AnyVal

trait JmapSettingEntry

object InboxArchivalPeriod {
  def parse(string: String): Option[Period] =
    string.toLowerCase(Locale.US) match {
      case "monthly" => Some(Period.ofMonths(1))
      case "yearly" => Some(Period.ofYears(1))
      case _ => None
    }
}

object InboxArchivalFormat extends Enumeration {
  type InboxArchivalFormat = Value
  val Single = Value("single")
  val Monthly = Value("monthly")
  val Yearly = Value("yearly")

  def parse(string: String): Option[InboxArchivalFormat] =
    string.toLowerCase(Locale.US) match {
      case "single" => Some(Single)
      case "monthly" => Some(Monthly)
      case "yearly" => Some(Yearly)
      case _ => None
    }
}

object JmapSettings {
  val trashCleanupEnabledSetting: JmapSettingsKey = JmapSettingsKey.liftOrThrow("trash.cleanup.enabled")
  val trashCleanupPeriodSetting: JmapSettingsKey = JmapSettingsKey.liftOrThrow("trash.cleanup.period")

  val spamCleanupEnabledSetting: JmapSettingsKey = JmapSettingsKey.liftOrThrow("spam.cleanup.enabled")
  val spamCleanupPeriodSetting: JmapSettingsKey = JmapSettingsKey.liftOrThrow("spam.cleanup.period")

  val weeklyPeriod: String = "weekly"
  val monthlyPeriod: String = "monthly"

  val cleanupDefaultPeriod: Period = Period.ofMonths(1)

  def parsePeriod(string: String): Option[Period] =
    string.toLowerCase(Locale.US) match {
      case JmapSettings.weeklyPeriod => Some(Period.ofWeeks(1))
      case JmapSettings.monthlyPeriod => Some(Period.ofMonths(1))
      case _ => None
    }

  val INBOX_ARCHIVAL_ENABLE_KEY = "inbox.archival.enabled"
  val INBOX_ARCHIVAL_PERIOD_KEY = "inbox.archival.period"
  val INBOX_ARCHIVAL_FORMAT_KEY = "inbox.archival.format"
  val LANGUAGE_KEY = "language"
  @Deprecated
  val AI_NEEDS_ACTION_ENABLE_KEY = "ai.needs-action.enabled"
  val AI_LABEL_CATEGORIZATION_ENABLE_KEY = "ai.label-categorization.enabled"
  val AI_RAG_ENABLE_KEY = "ai.rag.enabled"

  val INBOX_ARCHIVAL_ENABLE_DEFAULT_VALUE: Boolean = false
  val INBOX_ARCHIVAL_PERIOD_DEFAULT_VALUE: Period = Period.ofMonths(1)
  val INBOX_ARCHIVAL_FORMAT_DEFAULT_VALUE: InboxArchivalFormat = InboxArchivalFormat.Single
  val AI_LABEL_CATEGORIZATION_DISABLE_DEFAULT_VALUE: Boolean = false
  val AI_RAG_DISABLE_DEFAULT_VALUE: Boolean = false
}

case class JmapSettings(settings: Map[JmapSettingsKey, JmapSettingsValue], state: UuidState) {
  def this(settings: scala.collection.mutable.Map[JmapSettingsKey, JmapSettingsValue], state: UuidState) = {
    this(settings.toMap, state)
  }

  def trashCleanupEnabled(): Boolean =
    settings.get(trashCleanupEnabledSetting).exists(trashCleanupEnabled => trashCleanupEnabled.value.toBoolean)

  def trashCleanupPeriod(): Period =
    settings.get(trashCleanupPeriodSetting)
      .flatMap(trashCleanupPeriod => JmapSettings.parsePeriod(trashCleanupPeriod.value))
      .getOrElse(cleanupDefaultPeriod)

  def spamCleanupEnabled(): Boolean =
    settings.get(spamCleanupEnabledSetting).exists(spamCleanupEnabled => spamCleanupEnabled.value.toBoolean)

  def spamCleanupPeriod(): Period =
    settings.get(spamCleanupPeriodSetting)
      .flatMap(spamCleanupPeriod => JmapSettings.parsePeriod(spamCleanupPeriod.value))
      .getOrElse(cleanupDefaultPeriod)

  def inboxArchivalEnable(): Boolean =
    settings.get(JmapSettingsKey.liftOrThrow(INBOX_ARCHIVAL_ENABLE_KEY))
      .exists(value => Try(value.value.toBoolean)
        .fold(_ => INBOX_ARCHIVAL_ENABLE_DEFAULT_VALUE,
          bool => bool))

  def inboxArchivalPeriod(): Period =
    settings.get(JmapSettingsKey.liftOrThrow(INBOX_ARCHIVAL_PERIOD_KEY))
      .flatMap(value => InboxArchivalPeriod.parse(value.value))
      .getOrElse(INBOX_ARCHIVAL_PERIOD_DEFAULT_VALUE)

  def inboxArchivalFormat(): InboxArchivalFormat =
    settings.get(JmapSettingsKey.liftOrThrow(INBOX_ARCHIVAL_FORMAT_KEY))
      .flatMap(value => InboxArchivalFormat.parse(value.value))
      .getOrElse(INBOX_ARCHIVAL_FORMAT_DEFAULT_VALUE)

  def language(): Option[String] =
    settings.get(JmapSettingsKey.liftOrThrow(LANGUAGE_KEY))
      .map(_.value)

  def aiLabelCategorizationEnable(): Boolean =
    settings.get(JmapSettingsKey.liftOrThrow(AI_LABEL_CATEGORIZATION_ENABLE_KEY))
      .orElse(settings.get(JmapSettingsKey.liftOrThrow(AI_NEEDS_ACTION_ENABLE_KEY)))
        .map(newSetting => Try(newSetting.value.toBoolean)
        .getOrElse(AI_LABEL_CATEGORIZATION_DISABLE_DEFAULT_VALUE))
      .getOrElse(AI_LABEL_CATEGORIZATION_DISABLE_DEFAULT_VALUE)

  def aiRagEnable(): Boolean =
    settings.get(JmapSettingsKey.liftOrThrow(AI_RAG_ENABLE_KEY))
      .map(value => Try(value.value.toBoolean)
        .getOrElse(AI_RAG_DISABLE_DEFAULT_VALUE))
      .getOrElse(AI_RAG_DISABLE_DEFAULT_VALUE)
}

case class JmapSettingsUpsertRequest(settings: Map[JmapSettingsKey, JmapSettingsValue])

object JmapSettingsPatch {
  def merge(patch1: JmapSettingsPatch, patch2: JmapSettingsPatch): JmapSettingsPatch = patch1.merge(patch2)

  def toRemove(key: JmapSettingsKey): JmapSettingsPatch = JmapSettingsPatch(JmapSettingsUpsertRequest(Map()), Seq(key))

  def toUpsert(key: JmapSettingsKey, value: JmapSettingsValue): JmapSettingsPatch = JmapSettingsPatch(JmapSettingsUpsertRequest(Map(key -> value)), Seq())
}

case class JmapSettingsPatch(toUpsert: JmapSettingsUpsertRequest, toRemove: Seq[JmapSettingsKey]) {
  def isEmpty: Boolean = toUpsert.settings.isEmpty && toRemove.isEmpty

  def isConflict: Boolean = {
    val toUpsertKeys: Set[JmapSettingsKey] = toUpsert.settings.keySet
    val toRemoveKeys: Set[JmapSettingsKey] = toRemove.toSet
    toUpsertKeys.intersect(toRemoveKeys).nonEmpty
  }

  def merge(other: JmapSettingsPatch): JmapSettingsPatch = {
    val toUpsert: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(this.toUpsert.settings ++ other.toUpsert.settings)
    val toRemove: Seq[JmapSettingsKey] = this.toRemove ++ other.toRemove
    JmapSettingsPatch(toUpsert, toRemove)
  }
}

object SettingsStateUpdate {
  def from(oldState: UuidState): SettingsStateUpdate =
    SettingsStateUpdate(oldState, JmapSettingsStateFactory.generateState())
}

case class SettingsStateUpdate(oldState: UuidState, newState: UuidState)

object JmapSettingsStateFactory {
  def generateState(): UuidState =
    UuidState.fromGenerateUuid()

  val INITIAL: UuidState = UuidState.INSTANCE
}

trait JmapSettingParser[T <: JmapSettingEntry] {
  def key(): JmapSettingsKey

  def defaultValue(): JmapSettingsValue

  def parse(value: Option[JmapSettingsValue]): T
}

case object SettingsTypeName extends TypeName {
  override val asString: String = "Settings"

  override def parse(string: String): Option[TypeName] = string match {
    case SettingsTypeName.asString => Some(SettingsTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, State] = UuidState.parse(string)
}