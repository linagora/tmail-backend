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

package com.linagora.tmail.vault.metadata;

import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.reactivestreams.Publisher;

public interface TmailDeletedMessageMetadataVault extends DeletedMessageMetadataVault {
    /**
     * Lists the owners having (or having had) entries in the given bucket, whether or not they are
     * still listed in the UsersRepository (team mailboxes, deleted users...).
     */
    Publisher<Username> listOwners(BucketName bucketName);
}
