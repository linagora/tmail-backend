package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.model.JmapSettingsEntry.{JmapSettingsId, SETTING_SINGLETON_ID}
import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import com.linagora.tmail.james.jmap.settings.{JmapSettings, JmapSettingsStateFactory}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class JmapSettingsGet(accountId: AccountId,
                           ids: Option[Set[JmapSettingsId]] = None) extends WithAccountId
case class JmapSettingsResponse(accountId: AccountId,
                                state: UuidState,
                                list: Seq[JmapSettingsEntry],
                                notFound: Seq[JmapSettingsId])

object JmapSettingsEntry {

  type JmapSettingsId = Id

  val SETTING_SINGLETON_ID: JmapSettingsId = Id.validate("singleton").toOption.get

  def singleton(jmapSettings: JmapSettings): JmapSettingsEntry =
    JmapSettingsEntry(SETTING_SINGLETON_ID, jmapSettings.state, jmapSettings.settings)

  def unapplyIgnoreState(settingsEntry: JmapSettingsEntry): Some[(JmapSettingsId, Map[JmapSettingsKey, JmapSettingsValue])] =
    Some((settingsEntry.id, settingsEntry.settings))
}

case class JmapSettingsEntry(id: JmapSettingsId,
                             state: UuidState = JmapSettingsStateFactory.INITIAL,
                             settings: Map[JmapSettingsKey, JmapSettingsValue])

object JmapSettingsGetResult {

  def empty: JmapSettingsGetResult = JmapSettingsGetResult(Set(), Set())

  def notFound(id: JmapSettingsId): JmapSettingsGetResult = JmapSettingsGetResult(Set(), Set(id))

  def merge(r1: JmapSettingsGetResult, r2: JmapSettingsGetResult): JmapSettingsGetResult =
    JmapSettingsGetResult(r1.settingsEntries ++ r2.settingsEntries, r1.notFound ++ r2.notFound)

  def emptySingleton(): JmapSettingsGetResult =
    JmapSettingsGetResult(Set(JmapSettingsEntry(SETTING_SINGLETON_ID, JmapSettingsStateFactory.INITIAL, Map())), Set())

  def singleton(jmapSettings: JmapSettings): JmapSettingsGetResult =
    JmapSettingsGetResult(Set(JmapSettingsEntry.singleton(jmapSettings)), Set())
}

case class JmapSettingsGetResult(settingsEntries: Set[JmapSettingsEntry],
                                 notFound: Set[JmapSettingsId] = Set()) {
  def asResponse(accountId: AccountId): JmapSettingsResponse =
    JmapSettingsResponse(
      accountId = accountId,
      state = settingsEntries.headOption.map(_.state).getOrElse(JmapSettingsStateFactory.INITIAL),
      list = settingsEntries.toSeq,
      notFound = notFound.toSeq)
}