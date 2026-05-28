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

package com.linagora.tmail.james.jmap.blob;

import java.util.Optional;

import org.apache.james.blob.api.BlobId;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;

import reactor.core.publisher.Mono;

public interface UnauthenticatedBlobDownloadTokenRepository {
    Mono<UnauthenticatedBlobDownloadToken> generate(AccountId accountId, BlobId blobId, Username username);

    Mono<Optional<Username>> check(AccountId accountId, BlobId blobId, UnauthenticatedBlobDownloadToken token);
}
