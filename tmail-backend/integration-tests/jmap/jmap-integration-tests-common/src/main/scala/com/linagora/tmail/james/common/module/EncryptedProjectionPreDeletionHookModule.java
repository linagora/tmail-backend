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

package com.linagora.tmail.james.common.module;

import org.apache.james.modules.mailbox.PreDeletionHookConfiguration;
import org.apache.james.modules.mailbox.PreDeletionHooksConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.encrypted.DeleteEncryptedProjectionHook;

/**
 * Equivalent, for tests, of declaring the hook in listeners.xml:
 *
 * <pre>
 * &lt;preDeletionHook&gt;
 *     &lt;class&gt;com.linagora.tmail.encrypted.DeleteEncryptedProjectionHook&lt;/class&gt;
 * &lt;/preDeletionHook&gt;
 * </pre>
 */
public class EncryptedProjectionPreDeletionHookModule extends AbstractModule {
    @Provides
    @Singleton
    PreDeletionHooksConfiguration providePreDeletionHooksConfiguration() {
        return PreDeletionHooksConfiguration.forHooks(
            PreDeletionHookConfiguration.forClass(DeleteEncryptedProjectionHook.class.getName()));
    }
}
