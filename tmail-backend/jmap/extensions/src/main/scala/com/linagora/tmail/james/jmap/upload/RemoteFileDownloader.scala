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

package com.linagora.tmail.james.jmap.upload

import java.io.InputStream

import org.apache.james.jmap.api.model.UploadMetaData
import reactor.core.scala.publisher.SMono

case class DownloadedRemoteFile(contentType: Option[String], content: InputStream)

trait RemoteFileDownloader {
  def withDownloadedFile(remoteUrl: ValidatedRemoteUrl,
                         maximumSize: Long)
                        (use: DownloadedRemoteFile => SMono[UploadMetaData]): SMono[UploadMetaData]
}
