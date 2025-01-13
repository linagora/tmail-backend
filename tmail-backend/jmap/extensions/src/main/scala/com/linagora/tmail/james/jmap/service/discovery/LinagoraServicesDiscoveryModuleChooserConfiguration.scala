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

package com.linagora.tmail.james.jmap.service.discovery

import java.io.FileNotFoundException

import org.apache.james.utils.PropertiesProvider
import org.slf4j.{Logger, LoggerFactory}

case class LinagoraServicesDiscoveryModuleChooserConfiguration(enable: Boolean)

object LinagoraServicesDiscoveryModuleChooserConfiguration {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[LinagoraServicesDiscoveryModuleChooserConfiguration])

  def parse(propertiesProvider: PropertiesProvider): LinagoraServicesDiscoveryModuleChooserConfiguration =
    try {
      propertiesProvider.getConfiguration("linagora-ecosystem")
      LOGGER.info("Turned on Linagora services discovery module.")
      new LinagoraServicesDiscoveryModuleChooserConfiguration(true)
    } catch {
      case _: FileNotFoundException =>
        LOGGER.info("Turned off Linagora services discovery module.")
        new LinagoraServicesDiscoveryModuleChooserConfiguration(false)
    }
}
