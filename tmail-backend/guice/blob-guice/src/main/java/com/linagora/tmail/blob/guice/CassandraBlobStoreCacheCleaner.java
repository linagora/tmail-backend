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

package com.linagora.tmail.blob.guice;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.cassandra.cache.BlobStoreCache;
import org.reactivestreams.Publisher;

public class CassandraBlobStoreCacheCleaner implements BlobStoreCacheCleaner {
    private final BlobStoreCache blobStoreCache;

    @Inject
    public CassandraBlobStoreCacheCleaner(BlobStoreCache blobStoreCache) {
        this.blobStoreCache = blobStoreCache;
    }

    @Override
    public Publisher<Void> removeFromCache(BlobId blobId) {
        return blobStoreCache.remove(blobId);
    }
}
