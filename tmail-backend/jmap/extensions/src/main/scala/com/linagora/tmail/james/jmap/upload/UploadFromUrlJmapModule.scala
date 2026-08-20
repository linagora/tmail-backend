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

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.method.UploadFromUrlCapabilityFactory
import com.linagora.tmail.james.jmap.routes.UploadFromUrlRoutes
import org.apache.james.jmap.JMAPRoutes
import org.apache.james.jmap.core.CapabilityFactory

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
