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

package com.linagora.tmail.james.jmap.routes

import java.nio.charset.StandardCharsets

import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.json.ResponseSerializer
import play.api.libs.json.Json
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.HttpServerResponse

object DownloadResponseUtils {
  def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails): SMono[Unit] =
    if (httpServerResponse.hasSentHeaders) {
      // Response already committed (e.g. an error occurred while streaming the download body):
      // status and headers are already on the wire, so we cannot rewrite them into an error response.
      // Attempting to do so would throw `IllegalStateException: Status and headers already sent`, masking
      // the original error. The real cause is already logged by the caller; let the connection be closed.
      SMono.empty
    } else {
      SMono.fromCallable(() => ResponseSerializer.serialize(details))
        .map(Json.stringify)
        .map(_.getBytes(StandardCharsets.UTF_8))
        .flatMap(bytes =>
          SMono.fromPublisher(httpServerResponse.status(details.status)
            .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
            .header(CONTENT_LENGTH, Integer.toString(bytes.length))
            .sendByteArray(SMono.just(bytes))
            .`then`).`then`)
    }
}
