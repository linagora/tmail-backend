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

import java.net.URI

import jakarta.inject.Inject

import scala.util.Try

case class ValidatedRemoteUrl(uri: URI, requiresSsrfValidation: Boolean)

class RemoteUrlPolicy @Inject()(configuration: UploadFromUrlConfiguration) {
  def validate(value: String): ValidatedRemoteUrl =
    Try(new URI(value)).toOption.filter(isValidHttpsUri) match {
      case Some(uri) => validateSource(uri, value)
      case None => throw RemoteUrlRejectedException(value)
    }

  private def validateSource(uri: URI, value: String): ValidatedRemoteUrl = configuration.allowedSources match {
    case allowedSources if allowedSources.exists(_.matches(uri)) =>
      ValidatedRemoteUrl(uri, requiresSsrfValidation = false)
    // Restrict unconfigured SSRF-protected uploads to the standard HTTPS port.
    case Seq() if effectivePort(uri) == AllowedRemoteSource.DEFAULT_HTTPS_PORT =>
      ValidatedRemoteUrl(uri, requiresSsrfValidation = true)
    case _ => throw RemoteUrlRejectedException(value)
  }

  private def isValidHttpsUri(uri: URI): Boolean =
    uri.isAbsolute &&
      !uri.isOpaque &&
      Option(uri.getScheme).exists(_.equalsIgnoreCase("https")) &&
      Option(uri.getHost).flatMap(host => Try(AllowedRemoteSource.normalizeHost(host)).toOption).nonEmpty &&
      uri.getUserInfo == null &&
      uri.getFragment == null &&
      uri.getPort <= 65535

  private def effectivePort(uri: URI): Int = Option(uri.getPort).filter(_ >= 0).getOrElse(AllowedRemoteSource.DEFAULT_HTTPS_PORT)
}
