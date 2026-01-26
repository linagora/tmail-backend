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

package com.linagora.tmail.james.jmap.label

import java.util

import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelCreationRequest, LabelId}
import org.apache.james.core.Username
import org.reactivestreams.Publisher

trait LabelRepository {
  def addLabel(username: Username, labelCreationRequest: LabelCreationRequest): Publisher[Label]

  def addLabel(username: Username, label: Label): Publisher[Void]

  def addLabels(username: Username, labelCreationRequests: util.Collection[LabelCreationRequest]): Publisher[Label]

  def updateLabel(username: Username, labelId: LabelId, newDisplayName: Option[DisplayName] = None, newColor: Option[Color] = None, newDocumentation: Option[String] = None): Publisher[Void]

  def getLabels(username: Username, ids: util.Collection[LabelId]): Publisher[Label]

  def listLabels(username: Username): Publisher[Label]

  def deleteLabel(username: Username, labelId: LabelId): Publisher[Void]

  def deleteAllLabels(username: Username): Publisher[Void]
}