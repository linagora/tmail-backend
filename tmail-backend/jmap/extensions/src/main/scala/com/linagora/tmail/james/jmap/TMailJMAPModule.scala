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

package com.linagora.tmail.james.jmap

import java.io.FileNotFoundException

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.linagora.tmail.james.jmap.oidc.JMAPOidcConfiguration
import org.apache.commons.configuration2.{Configuration, PropertiesConfiguration}
import org.apache.james.mailbox.model.MessageId
import org.apache.james.mailbox.{MessageIdManager, SessionProvider}
import org.apache.james.utils.PropertiesProvider

import scala.util.Try

class TMailJMAPModule extends AbstractModule {

  val JMAP_CONFIGURATION_EMPTY: Configuration = new PropertiesConfiguration()

  @Provides
  @Singleton
  @Named("jmap")
  def provideJMAPConfiguration(propertiesProvider: PropertiesProvider): Configuration =
    Try(propertiesProvider.getConfiguration("jmap"))
      .fold({
        case _: FileNotFoundException => JMAP_CONFIGURATION_EMPTY
      }, identity)

  @Provides
  def provideJMAPExtensionConfiguration(@Named("jmap") configuration: Configuration): JMAPExtensionConfiguration = JMAPExtensionConfiguration.from(configuration)

  @Provides
  def providePublicAssetTotalSizeLimit(jmapExtensionConfiguration: JMAPExtensionConfiguration): PublicAssetTotalSizeLimit = jmapExtensionConfiguration.publicAssetTotalSizeLimit

  @Provides
  @Singleton
  def provideEventAttendanceRepository(calendarEventRepository: CalendarEventRepository): EventAttendanceRepository =
    calendarEventRepository

  @Provides
  @Singleton
  def provideCalendarEventRepository(messageIdManager: MessageIdManager, sessionProvider: SessionProvider, messageIdFactory: MessageId.Factory): CalendarEventRepository =
    new StandaloneEventRepository(messageIdManager, sessionProvider, messageIdFactory)

  @Provides
  @Singleton
  def provideJMAPOidcConfiguration(@Named("jmap") configuration: Configuration) =
    JMAPOidcConfiguration.parseConfiguration(configuration)
}
