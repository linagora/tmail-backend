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

package com.linagora.tmail.james.jmap.upload

import java.io.FileNotFoundException

import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Modules
import com.google.inject.{AbstractModule, Module, Scopes}
import com.linagora.tmail.james.jmap.method.UploadFromUrlCapabilityFactory
import com.linagora.tmail.james.jmap.routes.UploadFromUrlRoutes
import org.apache.commons.configuration2.ex.ConfigurationException
import org.apache.james.jmap.JMAPRoutes
import org.apache.james.jmap.core.CapabilityFactory
import org.apache.james.server.core.configuration.Configuration
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.apache.james.utils.PropertiesProvider

object UploadFromUrlJmapModule {
  def from(serverConfiguration: Configuration): Module = {
    val propertiesProvider = new PropertiesProvider(new FileSystemImpl(serverConfiguration.directories()), serverConfiguration.configurationPath())

    try {
      val jmapConfiguration = propertiesProvider.getConfiguration("jmap")
      val uploadConfiguration = UploadFromUrlConfiguration.from(jmapConfiguration)
      if (jmapConfiguration.getBoolean("enabled", true) && uploadConfiguration.isEnabled) {
        new UploadFromUrlJmapModule(uploadConfiguration)
      } else {
        Modules.EMPTY_MODULE
      }
    } catch {
      case _: FileNotFoundException => Modules.EMPTY_MODULE
      case exception: ConfigurationException => throw new RuntimeException("Unable to read jmap.properties for upload from URL", exception)
    }
  }
}

class UploadFromUrlJmapModule(configuration: UploadFromUrlConfiguration) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[UploadFromUrlConfiguration]).toInstance(configuration)
    bind(classOf[RemoteUrlPolicy]).in(Scopes.SINGLETON)
    bind(classOf[SsrfRemoteUrlValidator]).in(Scopes.SINGLETON)
    bind(classOf[RemoteFileDownloader]).to(classOf[ReactorNettyRemoteFileDownloader]).in(Scopes.SINGLETON)

    Multibinder.newSetBinder(binder(), classOf[JMAPRoutes])
      .addBinding()
      .to(classOf[UploadFromUrlRoutes])
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[UploadFromUrlCapabilityFactory])
  }
}
