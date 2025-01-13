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

package com.linagora.tmail.james.jmap

import org.apache.commons.configuration2.PropertiesConfiguration
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test

class JMAPExtensionConfigurationTest {

  @Test
  def shouldThrowWhenBothSupportMailAddressAndHttpLinkAreDefined(): Unit = {
    val configuration = new PropertiesConfiguration
    configuration.addProperty("support.mail.address", "support@linagora.abc")
    configuration.addProperty("support.httpLink", "http://linagora.abc/support")

    assertThatThrownBy(() => JMAPExtensionConfiguration.from(configuration))
      .isInstanceOf(classOf[IllegalArgumentException])
    assertThatThrownBy(() => JMAPExtensionConfiguration.from(configuration))
      .hasMessage("Both `support.mail.address` and `support.httpLink` must not be defined at the same time.")
  }

  @Test
  def shouldNotThrowWhenOnlySupportMailAddressIsDefined(): Unit = {
    val configuration = new PropertiesConfiguration
    configuration.addProperty("support.mail.address", "support@linagora.abc")

    assertThatCode(() => JMAPExtensionConfiguration.from(configuration))
      .doesNotThrowAnyException()

    assertThat(JMAPExtensionConfiguration.from(configuration).supportMailAddress.get.asString())
      .isEqualTo("support@linagora.abc")
  }

  @Test
  def shouldNotThrowWhenOnlySupportHttpLinkIsDefined(): Unit = {
    val configuration = new PropertiesConfiguration
    configuration.addProperty("support.httpLink", "http://linagora.abc/support")

    assertThatCode(() => JMAPExtensionConfiguration.from(configuration))
      .doesNotThrowAnyException()

    assertThat(JMAPExtensionConfiguration.from(configuration).supportHttpLink.get.toString)
      .isEqualTo("http://linagora.abc/support")
  }

  @Test
  def shouldNotThrowWhenNeitherSupportMailAddressNorHttpLinkIsDefined(): Unit = {
    val configuration = new PropertiesConfiguration

    assertThatCode(() => JMAPExtensionConfiguration.from(configuration))
      .doesNotThrowAnyException()

    assertThat(JMAPExtensionConfiguration.from(configuration).supportHttpLink.isEmpty).isTrue
    assertThat(JMAPExtensionConfiguration.from(configuration).supportHttpLink.isEmpty).isTrue
  }

}