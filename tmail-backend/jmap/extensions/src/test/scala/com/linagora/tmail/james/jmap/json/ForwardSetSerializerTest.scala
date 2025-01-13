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

package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{Forward, ForwardSetRequest, ForwardUpdateRequest, LocalCopy}
import org.apache.james.core.MailAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

class ForwardSetSerializerTest {

  @Test
  def deserializeForwardSetUpdateRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |        "localCopy": true,
        |        "forwards": [
        |            "targetA@domain.org",
        |            "targetB@domain.org"
        |        ]
        |    }""".stripMargin)

    val deserializeResult: JsResult[ForwardUpdateRequest] = ForwardSerializer.deserializeForwardSetUpdateRequest(jsInput)
    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .isEqualTo(ForwardUpdateRequest(localCopy = LocalCopy(true),
        forwards = Seq(Forward(new MailAddress("targetA@domain.org")), Forward(new MailAddress("targetB@domain.org")))
      ))
  }

  @Test
  def deserializeForwardSetRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |      "accountId": "ABC",
        |      "update": {
        |        "singleton": {
        |        "localCopy": true,
        |        "forwards": [
        |           "targetA@domain.org",
        |           "targetB@domain.org"
        |        ]
        |      }
        |      },
        |      "create": {
        |        "singleton": {
        |        "localCopy": true,
        |        "forwards": [
        |           "targetA@domain.org",
        |           "targetB@domain.org"
        |        ]
        |      }
        |      },
        |      "destroy": ["singleton"]
        |    }""".stripMargin)

    val deserializeResult: JsResult[ForwardSetRequest] = ForwardSerializer.deserializeForwardSetRequest(jsInput)
    assertThat(deserializeResult.isSuccess)
      .isTrue
  }
}
