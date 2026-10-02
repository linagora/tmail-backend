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

package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.saas.api.DomainSendingValidator
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository
import org.apache.james.core.Domain
import org.apache.james.server.core.MailImpl
import org.apache.mailet.{Attribute, AttributeValue, Mail}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class MailDnsConfigurationValidationTest {
  private val repository = new MemorySaaSAccountRepository()
  private val validation = new MailDnsConfigurationValidation(new DomainSendingValidator(repository))
  private val domain = Domain.of("migration.example")

  @Test
  def shouldRejectPendingDomainAndAcceptAfterValidation(): Unit = {
    val mail = MailImpl.builder().name("submission").sender("alice@migration.example")
      .addAttribute(new Attribute(Mail.JMAP_AUTH_USER, AttributeValue.of("alice@migration.example"))).build()
    try {
      Mono.from(repository.setMailDnsConfigurationValidated(domain, false)).block()
      assertThat(validation.validate(mail).block().get.`type`.value).isEqualTo("forbiddenToSend")
      Mono.from(repository.setMailDnsConfigurationValidated(domain, true)).block()
      assertThat(validation.validate(mail).block().isEmpty).isTrue
    } finally {
      mail.dispose()
    }
  }

  @Test
  def shouldCheckAuthenticatedDomainEvenWithAnotherSender(): Unit = {
    val mail = MailImpl.builder().name("submission").sender("alias@other.example")
      .addAttribute(new Attribute(Mail.JMAP_AUTH_USER, AttributeValue.of("alice@migration.example"))).build()
    try {
      Mono.from(repository.setMailDnsConfigurationValidated(domain, false)).block()
      assertThat(validation.validate(mail).block().get.`type`.value).isEqualTo("forbiddenToSend")
    } finally {
      mail.dispose()
    }
  }

  @Test
  def shouldRejectPendingEnvelopeDomainForAnActivatedAccount(): Unit = {
    val mail = MailImpl.builder().name("submission").sender("alias@migration.example")
      .addAttribute(new Attribute(Mail.JMAP_AUTH_USER, AttributeValue.of("alice@other.example"))).build()
    try {
      Mono.from(repository.setMailDnsConfigurationValidated(domain, false)).block()
      assertThat(validation.validate(mail).block().get.`type`.value).isEqualTo("forbiddenToSend")
    } finally {
      mail.dispose()
    }
  }

  @Test
  def shouldPreserveExistingDomainBehavior(): Unit = {
    val mail = MailImpl.builder().name("submission").sender("alice@migration.example").build()
    try {
      assertThat(validation.validate(mail).block().isEmpty).isTrue
    } finally {
      mail.dispose()
    }
  }
}
