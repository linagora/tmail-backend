package com.linagora.tmail.james.jmap.label

import java.util

import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelCreationRequest, LabelId}
import org.apache.james.core.Username
import org.reactivestreams.Publisher

trait LabelRepository {
  def addLabel(username: Username, labelCreationRequest: LabelCreationRequest): Publisher[Label]

  def addLabel(username: Username, label: Label): Publisher[Void]

  def addLabels(username: Username, labelCreationRequests: util.Collection[LabelCreationRequest]): Publisher[Label]

  def updateLabel(username: Username, labelId: LabelId, newDisplayName: Option[DisplayName] = None, newColor: Option[Color] = None): Publisher[Void]

  def getLabels(username: Username, ids: util.Collection[LabelId]): Publisher[Label]

  def listLabels(username: Username): Publisher[Label]

  def deleteLabel(username: Username, labelId: LabelId): Publisher[Void]

  def deleteAllLabels(username: Username): Publisher[Void]
}