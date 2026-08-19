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
 *******************************************************************/

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

sealed abstract class RemoteUploadException(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

case class RemoteUrlRejectedException(requestedUrl: String) extends RemoteUploadException("The remote URL is not allowed")

case class RemoteUploadInvalidRequestException() extends RemoteUploadException("The upload-from-URL request is invalid")

case class RemoteUploadTooLargeException(bestKnownSize: Option[Long] = None) extends RemoteUploadException("The remote file exceeds the upload size limit")

case class RemoteUploadCapacityException(cause: Throwable = null) extends RemoteUploadException("Remote upload capacity is unavailable", cause)

case class RemoteUploadTimeoutException(cause: Throwable = null) extends RemoteUploadException("The remote transfer timed out", cause)

case class RemoteUpstreamException(cause: Throwable = null) extends RemoteUploadException("The remote server response is not usable", cause)
