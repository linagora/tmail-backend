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

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.linagora.tmail.james.common.FirebaseSubscriptionProbeModule;
import com.linagora.tmail.james.common.FirebaseSubscriptionSetMethodContract;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;

public class PostgresFirebaseSubscriptionSetMethodTest implements FirebaseSubscriptionSetMethodContract {

    static Module FIREBASE_TEST_MODULE = new AbstractModule() {
        @Override
        protected void configure() {
            install(new FirebaseSubscriptionProbeModule());
            bind(FirebasePushClient.class).toInstance(FirebaseSubscriptionSetMethodContract.firebasePushClient());
        }
    };

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION.apply(FIREBASE_TEST_MODULE)
        .build();
}