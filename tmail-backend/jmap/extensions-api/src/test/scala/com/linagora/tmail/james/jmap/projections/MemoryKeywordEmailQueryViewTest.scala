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

package com.linagora.tmail.james.jmap.projections

import org.apache.james.mailbox.model.{MessageId, TestMessageId, ThreadId}

class MemoryKeywordEmailQueryViewTest extends KeywordEmailQueryViewContract {
  override val testee: KeywordEmailQueryView = new MemoryKeywordEmailQueryView()

  override val messageId1: MessageId = TestMessageId.of(1)
  override val messageId2: MessageId = TestMessageId.of(2)
  override val messageId3: MessageId = TestMessageId.of(3)
  override val messageId4: MessageId = TestMessageId.of(4)
  override val threadId1: ThreadId = ThreadId.fromBaseMessageId(messageId1)
  override val threadId2: ThreadId = ThreadId.fromBaseMessageId(messageId2)
  override val threadId3: ThreadId = ThreadId.fromBaseMessageId(messageId3)
}
