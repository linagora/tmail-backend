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

import java.net.{IDN, URI}
import java.util.Locale
import java.util.regex.Pattern

import com.google.common.base.CharMatcher

import scala.util.Try

object AllowedRemoteSource {
  val DEFAULT_HTTPS_PORT: Int = 443
  private val SOURCE_PATTERN: Pattern = Pattern.compile("(?i)^https://([^/:?#@]+)(?::([0-9]{1,5}))?$")
  private val LOWERCASE_ALPHANUMERIC: CharMatcher = CharMatcher.inRange('a', 'z')
    .or(CharMatcher.inRange('0', '9'))
  private val DNS_LABEL_CHARACTERS: CharMatcher = LOWERCASE_ALPHANUMERIC.or(CharMatcher.is('-'))
  private val WILDCARD_LITERAL_CHARACTERS: CharMatcher = DNS_LABEL_CHARACTERS.or(CharMatcher.inRange('A', 'Z'))

  def parse(value: String): Either[IllegalArgumentException, AllowedRemoteSource] =
    Try {
      val matcher = SOURCE_PATTERN.matcher(value.trim)
      if (!matcher.matches()) {
        throw new IllegalArgumentException("Expected an HTTPS remote source")
      }

      val hostPattern: String = matcher.group(1).stripSuffix(".")
      if (hostPattern.isEmpty || containsPercentEncoding(hostPattern)) {
        throw new IllegalArgumentException("Invalid remote source host pattern")
      }

      val port: Int = Option(matcher.group(2)).map(_.toInt).getOrElse(DEFAULT_HTTPS_PORT)
      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("Invalid HTTPS remote source port")
      }

      val labels: Seq[String] = hostPattern.split("\\.", -1).toSeq
      if (labels.exists(_.isEmpty)) {
        throw new IllegalArgumentException("Invalid DNS label")
      }

      val compiledLabels: Seq[String] = labels.map(compileLabel)
      AllowedRemoteSource(
        Pattern.compile("^" + compiledLabels.mkString("\\.") + "$"),
        port,
        value.trim)
    }.toEither.left.map {
      case exception: IllegalArgumentException => exception
      case exception => new IllegalArgumentException("Invalid HTTPS remote source", exception)
    }

  private[upload] def normalizeHost(host: String): String = {
    val normalized: String = IDN.toASCII(host.stripSuffix("."), IDN.USE_STD3_ASCII_RULES)
      .toLowerCase(Locale.US)
    val labels: Array[String] = normalized.split("\\.", -1)

    if (normalized.length > 253 || labels.exists(label => !isValidDnsLabel(label))) {
      throw new IllegalArgumentException("Invalid DNS host")
    }
    normalized
  }

  private def compileLabel(label: String): String = {
    val wildcardCount = label.count(_ == '%')
    if (wildcardCount == 0) {
      Pattern.quote(normalizeHost(label))
    } else if (wildcardCount == 1) {
      val parts: Array[String] = label.split("%", -1)
      if (parts.exists(part => !WILDCARD_LITERAL_CHARACTERS.matchesAllOf(part)) ||
        parts.headOption.exists(_.startsWith("-")) || parts.lastOption.exists(_.endsWith("-"))) {
        throw new IllegalArgumentException("Invalid wildcard DNS label")
      }
      Pattern.quote(parts.head.toLowerCase(Locale.US)) + "[a-z0-9-]+" + Pattern.quote(parts.last.toLowerCase(Locale.US))
    } else {
      throw new IllegalArgumentException("Only one wildcard is allowed per DNS label")
    }
  }

  private def containsPercentEncoding(value: String): Boolean =
    value.sliding(3).exists(candidate => candidate.length == 3 && candidate.head == '%' &&
      candidate.tail.forall(character => Character.digit(character, 16) >= 0))

  private def isValidDnsLabel(label: String): Boolean =
    label.nonEmpty &&
      label.length <= 63 &&
      DNS_LABEL_CHARACTERS.matchesAllOf(label) &&
      label.head != '-' &&
      label.last != '-'
}

case class AllowedRemoteSource private(hostPattern: Pattern,
                                       port: Int,
                                       private val advertisedValue: String) {
  def asString: String = advertisedValue

  def matches(uri: URI): Boolean =
    Option(uri.getScheme).exists(_.equalsIgnoreCase("https")) &&
      effectivePort(uri) == port &&
      Option(uri.getHost)
        .flatMap(host => Try(AllowedRemoteSource.normalizeHost(host)).toOption)
        .exists(host => hostPattern.matcher(host).matches())

  private def effectivePort(uri: URI): Int = Option(uri.getPort).filter(_ >= 0).getOrElse(AllowedRemoteSource.DEFAULT_HTTPS_PORT)
}
