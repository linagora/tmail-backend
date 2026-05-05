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

package com.linagora.tmail.james.jmap.domainsignature

import com.google.common.collect.ImmutableMap
import com.linagora.tmail.james.jmap.event.SignatureTextFactory.SignatureText
import com.linagora.tmail.james.jmap.event.{DomainBasedSignatureTextFactory, DomainSignatureTemplateRepository}
import com.unboundid.ldap.sdk.{Filter, LDAPConnectionPool, SearchScope}
import jakarta.inject.Inject
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.api.identity.{IdentityHtmlSignatureUpdate, IdentityRepository, IdentityTextSignatureUpdate, IdentityUpdateRequest}
import org.apache.james.jmap.api.model.{HtmlSignature, Identity, TextSignature}
import org.apache.james.user.api.UsersRepository
import org.apache.james.user.ldap.LdapRepositoryConfiguration
import org.apache.james.util.ReactorUtils
import org.slf4j.LoggerFactory
import reactor.core.publisher.{Flux, Mono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

class DomainSignatureTemplateApplyService @Inject()(
    repository: DomainSignatureTemplateRepository,
    usersRepository: UsersRepository,
    signatureTextFactory: DomainBasedSignatureTextFactory,
    identityRepository: IdentityRepository,
    ldapConnectionPool: LDAPConnectionPool,
    ldapConfiguration: LdapRepositoryConfiguration) {

  private val logger = LoggerFactory.getLogger(classOf[DomainSignatureTemplateApplyService])

  def apply(domain: Domain): Mono[ApplyResult] =
    repository.get(domain)
      .flatMap { opt =>
        if (opt.isPresent) applyForDomain(domain)
        else Mono.error(new DomainTemplateNotFoundException(domain))
      }

  private def applyForDomain(domain: Domain): Mono[ApplyResult] =
    Flux.from(usersRepository.listUsersOfADomainReactive(domain))
      .flatMap((user: Username) => applyForUser(user), ReactorUtils.LOW_CONCURRENCY)
      .reduce(new ApplyResult(0, 0, 0), (a: ApplyResult, b: ApplyResult) => a.merge(b))

  private def applyForUser(user: Username): Mono[ApplyResult] =
    Flux.from(identityRepository.list(user))
      .filter((identity: Identity) => identity.mayDelete.value)
      .filter((identity: Identity) => identity.sortOrder == 0)
      .next()
      .flatMap((identity: Identity) => updateIdentitySignature(user, identity))
      .defaultIfEmpty(ApplyResult.SKIPPED)
      .onErrorResume { e =>
        logger.warn("Failed to apply signature for user {}", user.asString(), e)
        Mono.just(ApplyResult.ERROR)
      }

  private def updateIdentitySignature(user: Username, identity: Identity): Mono[ApplyResult] =
    if (identity.textSignature.name.nonEmpty || identity.htmlSignature.name.nonEmpty)
      Mono.just(ApplyResult.SKIPPED)
    else
      signatureTextFactory.forUser(user)
        .flatMap { opt =>
          if (opt.isPresent) doApplySignature(user, identity, opt.get())
          else Mono.just(ApplyResult.SKIPPED)
        }

  private def doApplySignature(user: Username, identity: Identity, signature: SignatureText): Mono[ApplyResult] =
    Mono.fromCallable(() => fetchLdapAttributes(user))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .flatMap { ldapAttrs =>
        val interpolated = signature.interpolate(ldapAttrs)
        val updateRequest = IdentityUpdateRequest(
          textSignature = Some(IdentityTextSignatureUpdate(TextSignature(interpolated.textSignature()))),
          htmlSignature = Some(IdentityHtmlSignatureUpdate(HtmlSignature(interpolated.htmlSignature()))))
        Mono.from(identityRepository.update(user, identity.id, updateRequest))
          .then(Mono.just(ApplyResult.APPLIED))
      }

  private def fetchLdapAttributes(username: Username): java.util.Map[String, String] = {
    val userBase = username.getDomainPart.toScala
      .map(domain => ldapConfiguration.getPerDomainBaseDN.getOrDefault(domain, ldapConfiguration.getUserBase))
      .getOrElse(ldapConfiguration.getUserBase)
    val ldapAttr = if (username.asString.contains("@")) ldapConfiguration.getUserIdAttribute
                   else ldapConfiguration.getResolveLocalPartAttribute.orElse(ldapConfiguration.getUserIdAttribute)
    val filter = Filter.createEqualityFilter(ldapAttr, username.asString)
    val entries = ldapConnectionPool.search(userBase, SearchScope.SUB, filter).getSearchEntries.asScala
    entries.headOption match {
      case None => java.util.Collections.emptyMap()
      case Some(entry) =>
        entry.getAttributes.asScala
          .filter(_.getValue != null)
          .foldLeft(ImmutableMap.builder[String, String]())((builder, attr) => builder.put(attr.getName, attr.getValue))
          .build()
    }
  }
}
