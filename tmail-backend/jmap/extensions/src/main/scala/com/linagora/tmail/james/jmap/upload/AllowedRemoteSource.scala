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
import java.util.regex.{Matcher, Pattern}

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
      val source = value.trim
      val matcher = sourceMatcher(source)
      val hostPattern = validateHostPattern(matcher.group(1).stripSuffix("."))

      AllowedRemoteSource(compileHostPattern(hostPattern), parsePort(Option(matcher.group(2))), source)
    }.toEither.left.map {
      case exception: IllegalArgumentException => exception
      case exception => new IllegalArgumentException("Invalid HTTPS remote source", exception)
    }

  private def sourceMatcher(source: String): Matcher = {
    val matcher = SOURCE_PATTERN.matcher(source)
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Expected an HTTPS remote source")
    }
    matcher
  }

  private def validateHostPattern(hostPattern: String): String = {
    if (hostPattern.isEmpty || containsPercentEncoding(hostPattern)) {
      throw new IllegalArgumentException("Invalid remote source host pattern")
    }
    if (hostPattern.split("\\.", -1).exists(_.isEmpty)) {
      throw new IllegalArgumentException("Invalid DNS label")
    }
    hostPattern
  }

  private def parsePort(configuredPort: Option[String]): Int = {
    val port = configuredPort.map(_.toInt).getOrElse(DEFAULT_HTTPS_PORT)
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("Invalid HTTPS remote source port")
    }
    port
  }

  private def compileHostPattern(hostPattern: String): Pattern =
    Pattern.compile("^" + hostPattern.split("\\.", -1).map(compileLabel).mkString("\\.") + "$")

  private[upload] def normalizeHost(host: String): String = {
    val normalized: String = IDN.toASCII(host.stripSuffix("."), IDN.USE_STD3_ASCII_RULES)
      .toLowerCase(Locale.US)
    val labels: Array[String] = normalized.split("\\.", -1)

    if (normalized.length > 253 || labels.exists(label => !isValidDnsLabel(label))) {
      throw new IllegalArgumentException("Invalid DNS host")
    }
    normalized
  }

  private def compileLabel(label: String): String = label.count(_ == '%') match {
    case 0 => Pattern.quote(normalizeHost(label))
    case 1 => compileWildcardLabel(label)
    case _ => throw new IllegalArgumentException("Only one wildcard is allowed per DNS label")
  }

  private def compileWildcardLabel(label: String): String = {
    val parts: Array[String] = label.split("%", -1)
    if (parts.exists(part => !WILDCARD_LITERAL_CHARACTERS.matchesAllOf(part)) ||
      parts.head.startsWith("-") || parts.last.endsWith("-")) {
      throw new IllegalArgumentException("Invalid wildcard DNS label")
    }
    Pattern.quote(parts.head.toLowerCase(Locale.US)) + "[a-z0-9-]+" + Pattern.quote(parts.last.toLowerCase(Locale.US))
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
