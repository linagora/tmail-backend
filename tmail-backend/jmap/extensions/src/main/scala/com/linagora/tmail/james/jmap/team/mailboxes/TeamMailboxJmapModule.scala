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

package com.linagora.tmail.james.jmap.team.mailboxes

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.method.{TeamMailboxMemberGetMethod, TeamMailboxMemberSetMethod, TeamMailboxRevokeAccessMethod}
import com.linagora.tmail.team.TeamMailboxCallback
import org.apache.james.jmap.mail.NamespaceFactory
import org.apache.james.jmap.method.Method

class TeamMailboxJmapModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[NamespaceFactory]).to(classOf[TMailNamespaceFactory])
    install(new TeamMailboxesCapabilitiesModule())
    Multibinder.newSetBinder(binder, classOf[Method])
      .addBinding()
      .to(classOf[TeamMailboxRevokeAccessMethod])

    Multibinder.newSetBinder(binder, classOf[Method])
      .addBinding()
      .to(classOf[TeamMailboxMemberGetMethod])
    Multibinder.newSetBinder(binder, classOf[Method])
      .addBinding()
      .to(classOf[TeamMailboxMemberSetMethod])

    Multibinder.newSetBinder(binder, classOf[TeamMailboxCallback])
      .addBinding()
      .to(classOf[TeamMailboxAutocompleteCallback])
  }
}
