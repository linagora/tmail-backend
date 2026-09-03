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

package org.apache.james.mailbox.store;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Allows sharing mailboxes with entries whose domain differs from the mailbox owner one, as
 * {@code james.rights.crossdomain.allow=true} would.
 *
 * Lives in the {@code org.apache.james.mailbox.store} package to reach the package-private test hook of
 * {@link StoreRightManager} without relying on reflection.
 */
public class AllowCrossDomainAccessExtension implements BeforeAllCallback, AfterAllCallback {
    private Boolean previousValue;

    @Override
    public void beforeAll(ExtensionContext context) {
        previousValue = StoreRightManager.IS_CROSS_DOMAIN_ACCESS_ALLOWED;
        StoreRightManager.IS_CROSS_DOMAIN_ACCESS_ALLOWED = true;
    }

    @Override
    public void afterAll(ExtensionContext context) {
        StoreRightManager.IS_CROSS_DOMAIN_ACCESS_ALLOWED = previousValue;
    }
}
