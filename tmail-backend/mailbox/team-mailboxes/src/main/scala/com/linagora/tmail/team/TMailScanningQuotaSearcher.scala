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

package com.linagora.tmail.team

import java.util.Comparator

import com.google.inject.{AbstractModule, Scopes}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.quota.search.scanning.ClauseConverter
import org.apache.james.quota.search.{Limit, QuotaQuery, QuotaSearcher}
import org.apache.james.user.api.UsersRepository
import reactor.core.scala.publisher.SFlux

import scala.jdk.CollectionConverters._
import scala.math.Ordering.comparatorToOrdering

class TMailScanningQuotaSearcherModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[TMailScanningQuotaSearcher]).in(Scopes.SINGLETON)
    bind(classOf[QuotaSearcher]).to(classOf[TMailScanningQuotaSearcher])
  }
}

class TMailScanningQuotaSearcher @Inject()(usersRepository: UsersRepository,
                                           clauseConverter: ClauseConverter,
                                           teamMailboxRepository: TeamMailboxRepository) extends QuotaSearcher {
  override def search(query: QuotaQuery): java.util.List[Username] = {
    val ordering: Ordering[Username] = comparatorToOrdering(Comparator.comparing(user => user.asString))
    limit(listUsers
      .filter(user => clauseConverter.andToPredicate(query.getClause).test(user))
      .sorted(ordering)
      .drop(query.getOffset.getValue), query.getLimit).toList.asJava
  }

  private def limit(results: Seq[Username], limit: Limit): Seq[Username] =
    limit.getValue.map(l => results.take(l)).orElse(results)

  private def listUsers: Seq[Username] = usersRepository.list().asScala.toSeq ++
    SFlux(teamMailboxRepository.listTeamMailboxes())
      .map(teamMailbox => teamMailbox.asMailAddress)
      .map(Username.fromMailAddress)
      .collectSeq()
      .block()
}
