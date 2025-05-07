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

package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.net.URL;
import java.util.Optional;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.name.Names;
import com.linagora.tmail.common.OidcAuthenticationContract;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.oidc.JMAPOidcModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryOidcAuthenticationTest extends OidcAuthenticationContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> binder.bind(URL.class).annotatedWith(Names.named("userInfo"))
                .toProvider(OidcAuthenticationContract::getUserInfoTokenEndpoint))
            .overrideWith(binder -> binder.bind(IntrospectionEndpoint.class)
                .toProvider(() -> new IntrospectionEndpoint(getIntrospectTokenEndpoint(), Optional.empty()))))
        .build();
}
