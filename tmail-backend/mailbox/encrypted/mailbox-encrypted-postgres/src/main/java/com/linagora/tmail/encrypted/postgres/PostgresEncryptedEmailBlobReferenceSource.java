/** ******************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 * *
 * This file was taken and adapted from the Apache James project.  *
 * *
 * https://james.apache.org                                        *
 * *
 * It was originally licensed under the Apache V2 license.         *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                      *
 * ****************************************************************** */

package com.linagora.tmail.encrypted.postgres;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobReferenceSource;
import org.reactivestreams.Publisher;

public class PostgresEncryptedEmailBlobReferenceSource implements BlobReferenceSource {
    private final PostgresEncryptedEmailStoreDAO encryptedEmailStoreDAO;

    @Inject
    public PostgresEncryptedEmailBlobReferenceSource(PostgresEncryptedEmailStoreDAO encryptedEmailStoreDAO) {
        this.encryptedEmailStoreDAO = encryptedEmailStoreDAO;
    }

    @Override
    public Publisher<BlobId> listReferencedBlobs() {
        return encryptedEmailStoreDAO.getAllBlobIds();
    }
}
