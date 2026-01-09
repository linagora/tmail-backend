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

package com.linagora.tmail.blob.blobid.list;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDataDefinition;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdList;
import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdListDAO;
import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdListModule;

public class SingleSaveBlobStoreTest implements SingleSaveBlobStoreContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraDataDefinition.aggregateModules(CassandraBlobIdListModule.MODULE(),
                    CassandraSchemaVersionDataDefinition.MODULE));

    private CassandraBlobIdList cassandraBlobIdList;
    private BlobStoreDAO blobStoreDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraBlobIdListDAO cassandraBlobIdListDAO = new CassandraBlobIdListDAO(cassandra.getConf());
        cassandraBlobIdList = new CassandraBlobIdList(cassandraBlobIdListDAO);
        blobStoreDAO = new SingleSaveBlobStoreDAO(new MemoryBlobStoreDAO(), cassandraBlobIdList, defaultBucketName());
    }

    @Override
    public BlobStoreDAO testee() {
        return blobStoreDAO;
    }

    @Override
    public BlobIdList blobIdList() {
        return cassandraBlobIdList;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new PlainBlobId.Factory();
    }

    @Override
    public BucketName defaultBucketName() {
        return BucketName.DEFAULT;
    }
}
