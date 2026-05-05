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

package com.linagora.tmail.james.jmap.event

import java.util.{Locale, Optional}

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, selectFrom, update}
import com.datastax.oss.driver.api.querybuilder.relation.Relation.column
import com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition.{DOMAIN, SIGNATURE_HTML_PER_LANGUAGE, SIGNATURE_TEXT_PER_LANGUAGE, TABLE_NAME}
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Domain
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters._

class CassandraDomainSignatureTemplateRepository @Inject()(session: CqlSession) extends DomainSignatureTemplateRepository {

  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val selectStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .columns(SIGNATURE_TEXT_PER_LANGUAGE, SIGNATURE_HTML_PER_LANGUAGE)
    .where(column(DOMAIN).isEqualTo(bindMarker(DOMAIN)))
    .build())

  private val upsertStatement: PreparedStatement = session.prepare(update(TABLE_NAME)
    .setColumn(SIGNATURE_TEXT_PER_LANGUAGE, bindMarker(SIGNATURE_TEXT_PER_LANGUAGE))
    .setColumn(SIGNATURE_HTML_PER_LANGUAGE, bindMarker(SIGNATURE_HTML_PER_LANGUAGE))
    .where(column(DOMAIN).isEqualTo(bindMarker(DOMAIN)))
    .build())

  private def fromRow(row: com.datastax.oss.driver.api.core.cql.Row): Optional[DomainSignatureTemplate] = {
    val textMap = Option(row.getMap(SIGNATURE_TEXT_PER_LANGUAGE, classOf[String], classOf[String]))
    val htmlMap = Option(row.getMap(SIGNATURE_HTML_PER_LANGUAGE, classOf[String], classOf[String]))
    val langs = textMap.map(_.keySet.asScala).getOrElse(Set.empty) ++
                htmlMap.map(_.keySet.asScala).getOrElse(Set.empty)
    if (langs.isEmpty) {
      Optional.empty[DomainSignatureTemplate]()
    } else {
      val templates = langs.map { lang =>
        val locale = Locale.forLanguageTag(lang)
        val text = textMap.flatMap(m => Option(m.get(lang))).getOrElse("")
        val html = htmlMap.flatMap(m => Option(m.get(lang))).getOrElse("")
        locale -> new SignatureText(text, html)
      }.toMap
      Optional.of(new DomainSignatureTemplate(templates.asJava))
    }
  }

  override def get(domain: Domain): Mono[Optional[DomainSignatureTemplate]] =
    executor.executeSingleRowOptional(selectStatement.bind()
        .set(DOMAIN, domain.asString(), TypeCodecs.TEXT))
      .map(rowOpt => rowOpt.flatMap[DomainSignatureTemplate](fromRow))

  override def store(domain: Domain, template: DomainSignatureTemplate): Mono[Void] = {
    val textMap: java.util.Map[String, String] = template.templates.asScala
      .map { case (locale, sig) => locale.toLanguageTag -> sig.textSignature() }
      .toMap.asJava
    val htmlMap: java.util.Map[String, String] = template.templates.asScala
      .map { case (locale, sig) => locale.toLanguageTag -> sig.htmlSignature() }
      .toMap.asJava
    executor.executeVoid(upsertStatement.bind()
      .set(DOMAIN, domain.asString(), TypeCodecs.TEXT)
      .setMap(SIGNATURE_TEXT_PER_LANGUAGE, textMap, classOf[String], classOf[String])
      .setMap(SIGNATURE_HTML_PER_LANGUAGE, htmlMap, classOf[String], classOf[String]))
  }

  override def delete(domain: Domain): Mono[Void] =
    executor.executeVoid(upsertStatement.bind()
      .setToNull(SIGNATURE_TEXT_PER_LANGUAGE)
      .setToNull(SIGNATURE_HTML_PER_LANGUAGE)
      .set(DOMAIN, domain.asString(), TypeCodecs.TEXT))
}
