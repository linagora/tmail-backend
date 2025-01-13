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

package com.linagora.tmail.blob.blobid.list.cassandra

import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.blob.blobid.list.BlobIdList
import jakarta.inject.Inject
import org.apache.james.blob.api.BlobId
import org.reactivestreams.Publisher

case class BlobIdListCassandraModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraBlobIdListDAO]).in(Scopes.SINGLETON)
    bind(classOf[CassandraBlobIdList]).in(Scopes.SINGLETON)

    bind(classOf[BlobIdList]).to(classOf[CassandraBlobIdList])
  }
}

class CassandraBlobIdList @Inject()(cassandraBlobIdListDAO: CassandraBlobIdListDAO) extends BlobIdList {

  override def isStored(blobId: BlobId): Publisher[java.lang.Boolean] =
    cassandraBlobIdListDAO.isStored(blobId)

  override def store(blobId: BlobId): Publisher[Unit] =
    cassandraBlobIdListDAO.insert(blobId)

  override def remove(blobId: BlobId): Publisher[Unit] =
    cassandraBlobIdListDAO.remove(blobId)
}
