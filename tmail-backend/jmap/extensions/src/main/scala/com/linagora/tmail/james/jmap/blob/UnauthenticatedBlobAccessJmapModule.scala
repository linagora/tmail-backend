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

package com.linagora.tmail.james.jmap.blob

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.method.{UnauthenticatedBlobAccessCapabilityFactory, UnauthenticatedBlobAccessSetMethod}
import com.linagora.tmail.james.jmap.routes.UnauthenticatedBlobAccessDownloadRoutes
import org.apache.james.jmap.JMAPRoutes
import org.apache.james.jmap.core.CapabilityFactory
import org.apache.james.jmap.method.Method

class UnauthenticatedBlobAccessJmapModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[UnauthenticatedBlobAccessCapabilityFactory])

    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[UnauthenticatedBlobAccessSetMethod])

    Multibinder.newSetBinder(binder(), classOf[JMAPRoutes])
      .addBinding()
      .to(classOf[UnauthenticatedBlobAccessDownloadRoutes])
  }
}
