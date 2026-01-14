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

package com.linagora.tmail.integration.probe;

import org.apache.james.utils.GuiceProbe;

import com.google.inject.Inject;
import com.linagora.tmail.vault.blob.TmailBlobStoreDeletedMessageVault;

public class TmailBlobStoreDeletedMessageVaultProbe implements GuiceProbe {
    private final TmailBlobStoreDeletedMessageVault vault;

    @Inject
    public TmailBlobStoreDeletedMessageVaultProbe(TmailBlobStoreDeletedMessageVault vault) {
        this.vault = vault;
    }

    public TmailBlobStoreDeletedMessageVault getVault() {
        return vault;
    }
}
