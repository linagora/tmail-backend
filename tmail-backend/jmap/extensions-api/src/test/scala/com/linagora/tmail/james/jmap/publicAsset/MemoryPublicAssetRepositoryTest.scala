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

package com.linagora.tmail.james.jmap.publicAsset

import com.linagora.tmail.james.jmap.PublicAssetTotalSizeLimit
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetRepositoryContract.PUBLIC_ASSET_URI_PREFIX
import org.apache.james.blob.api.BucketName
import org.apache.james.blob.memory.MemoryBlobStoreDAO
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore
import org.junit.jupiter.api.BeforeEach

class MemoryPublicAssetRepositoryTest extends PublicAssetRepositoryContract {

  var memoryPublicAssetRepository: MemoryPublicAssetRepository = _

  @BeforeEach
  def setup(): Unit = {

    val blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO, blobIdFactory)
    memoryPublicAssetRepository = new MemoryPublicAssetRepository(blobStore, PublicAssetTotalSizeLimit.DEFAULT, PUBLIC_ASSET_URI_PREFIX)
  }

  override def teste: PublicAssetRepository = memoryPublicAssetRepository
}
