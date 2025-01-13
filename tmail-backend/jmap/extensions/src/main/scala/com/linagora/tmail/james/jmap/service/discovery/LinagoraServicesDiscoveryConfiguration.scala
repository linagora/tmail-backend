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

package com.linagora.tmail.james.jmap.service.discovery;

import org.apache.commons.configuration2.Configuration

import scala.jdk.CollectionConverters._

case class LinagoraServicesDiscoveryItem(key: String, value: String)

case class LinagoraServicesDiscoveryConfiguration(services: List[LinagoraServicesDiscoveryItem]) {
  def getServicesAsJava(): java.util.List[LinagoraServicesDiscoveryItem] = services.asJava
}

object LinagoraServicesDiscoveryConfiguration {
  def from(configuration: Configuration): LinagoraServicesDiscoveryConfiguration = {
    val services: List[LinagoraServicesDiscoveryItem] = configuration.getKeys
      .asScala
      .toList
      .map(key => LinagoraServicesDiscoveryItem(key, configuration.getString(key)))

    LinagoraServicesDiscoveryConfiguration(services)
  }
}