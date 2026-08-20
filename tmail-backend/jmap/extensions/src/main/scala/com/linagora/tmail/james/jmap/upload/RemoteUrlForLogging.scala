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

import scala.util.Try

object RemoteUrlForLogging {
  val INVALID_URL: String = "<invalid>"
  private val MAX_FILE_NAME_LENGTH: Int = 255
  private val REDACTED_PATH_PREFIX: String = "/…/"

  def sanitize(value: String): String =
    Try(URI.create(value))
      .filter(uri => uri.isAbsolute && uri.getHost != null)
      .map(uri => origin(uri) + fileName(uri).map(REDACTED_PATH_PREFIX + _).getOrElse(""))
      .getOrElse(INVALID_URL)

  private def origin(uri: URI): String =
    new URI(uri.getScheme, null, uri.getHost, uri.getPort, null, null, null).toASCIIString

  private def fileName(uri: URI): Option[String] =
    Option(uri.getRawPath)
      .filter(path => path.nonEmpty && path != "/" && !path.endsWith("/"))
      .flatMap(_.split("/", -1).lastOption)
      .filter(_.nonEmpty)
      .map(_.take(MAX_FILE_NAME_LENGTH))
}
