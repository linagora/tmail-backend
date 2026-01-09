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

package com.linagora.tmail.james.jmap.publicAsset;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresPublicAssetRepositoryTest implements PublicAssetRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PublicAssetTable.MODULE());

    private PublicAssetRepository publicAssetRepository;

    @BeforeEach
    void setup() {
        publicAssetRepository = new PostgresPublicAssetRepository(postgresExtension.getExecutorFactory(),
            blobIdFactory(),
            new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), blobIdFactory()),
            PublicAssetRepositoryContract.PUBLIC_ASSET_URI_PREFIX(),
            postgresExtension.getByPassRLSPostgresExecutor());
    }

    @Override
    public PublicAssetRepository teste() {
        return publicAssetRepository;
    }
}
