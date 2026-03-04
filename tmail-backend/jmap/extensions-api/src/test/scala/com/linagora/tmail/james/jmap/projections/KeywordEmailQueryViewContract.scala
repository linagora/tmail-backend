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

import java.time.ZonedDateTime

import org.apache.james.core.Username
import org.apache.james.jmap.mail.Keyword
import org.apache.james.mailbox.model.{MessageId, ThreadId}
import org.apache.james.util.streams.Limit
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SFlux

import scala.jdk.CollectionConverters._

object KeywordEmailQueryViewContract {
  val ALICE: Username = Username.of("alice@domain.tld")
  val BOB: Username = Username.of("bob@domain.tld")
  val DATE_1: ZonedDateTime = ZonedDateTime.parse("2010-10-30T15:12:00Z")
  val DATE_2: ZonedDateTime = ZonedDateTime.parse("2010-10-30T16:12:00Z")
  val DATE_3: ZonedDateTime = ZonedDateTime.parse("2010-10-30T17:12:00Z")
  val DATE_4: ZonedDateTime = ZonedDateTime.parse("2010-10-30T18:12:00Z")
  val DATE_5: ZonedDateTime = ZonedDateTime.parse("2010-10-30T19:12:00Z")

  val KEYWORD_A: Keyword = Keyword.of("keywordA").get
  val KEYWORD_B: Keyword = Keyword.of("keywordB").get
  val KEYWORD_C: Keyword = Keyword.of("keywordC").get
}

trait KeywordEmailQueryViewContract {
  import KeywordEmailQueryViewContract._

  def testee: KeywordEmailQueryView

  def messageId1: MessageId
  def messageId2: MessageId
  def messageId3: MessageId
  def messageId4: MessageId
  def threadId1: ThreadId
  def threadId2: ThreadId
  def threadId3: ThreadId

  @Test
  def listShouldReturnEmptyByDefault(): Unit = {
    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(12), false)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listShouldReturnMessagesSortedByReceivedAtDesc(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId3, threadId3).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId2, messageId3, messageId1)
  }

  @Test
  def listShouldFilterByKeyword(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_B, DATE_4, messageId2, threadId2).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId1)
  }

  @Test
  def listShouldApplyLimit(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId3, threadId3).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(2), false)).collectSeq().block().asJava)
      .containsExactly(messageId2, messageId3)
  }

  @Test
  def listSinceAfterShouldIncludeBoundaryAndBeSorted(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId3, threadId3).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeywordSinceAfter(ALICE, KEYWORD_A, DATE_2, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId2, messageId3)
  }

  @Test
  def listSinceAfterShouldReturnEmptyWhenNoneMatch(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId2, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId3, threadId3).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeywordSinceAfter(ALICE, KEYWORD_A, DATE_5, Limit.limit(12), false)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listBeforeShouldExcludeBoundaryAndBeSorted(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId3, threadId3).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeywordBefore(ALICE, KEYWORD_A, DATE_2, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId1)
  }

  @Test
  def listBeforeShouldReturnEmptyWhenNoneMatch(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_4, messageId2, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_5, messageId3, threadId3).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeywordBefore(ALICE, KEYWORD_A, DATE_1, Limit.limit(12), false)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def listShouldCollapseThreadsWhenRequested(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId3, threadId2).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(12), true)).collectSeq().block().asJava)
      .containsExactlyInAnyOrder(messageId2, messageId3)
  }

  @Test
  def listShouldApplyLimitWithCollapseThreads(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId3, threadId3).block()
    testee.save(ALICE, KEYWORD_A, DATE_4, messageId4, threadId1).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(2), true)).collectSeq().block().asJava)
      .containsExactly(messageId4, messageId3)
  }

  @Test
  def listSinceAfterShouldCollapseThreadsWhenRequested(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_5, messageId3, threadId1).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeywordSinceAfter(ALICE, KEYWORD_A, DATE_2, Limit.limit(12), true)).collectSeq().block().asJava)
      .containsExactly(messageId3)
  }

  @Test
  def listSinceAfterShouldApplyLimitWithCollapseThreads(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId3).block()
    testee.save(ALICE, KEYWORD_A, DATE_5, messageId3, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_4, messageId4, threadId1).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeywordSinceAfter(ALICE, KEYWORD_A, DATE_1, Limit.limit(2), true)).collectSeq().block().asJava)
      .containsExactly(messageId3, messageId2)
  }

  @Test
  def listBeforeShouldCollapseThreadsWhenRequested(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId2, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId3, threadId1).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeywordBefore(ALICE, KEYWORD_A, DATE_3, Limit.limit(12), true)).collectSeq().block().asJava)
      .containsExactly(messageId2)
  }

  @Test
  def listBeforeShouldApplyLimitWithCollapseThreads(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId2).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_5, messageId3, threadId3).block()
    testee.save(ALICE, KEYWORD_A, DATE_4, messageId4, threadId1).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeywordBefore(ALICE, KEYWORD_A, DATE_4, Limit.limit(2), true)).collectSeq().block().asJava)
      .containsExactly(messageId2, messageId1)
  }

  @Test
  def listShouldNotCollapseThreadsWhenNotRequested(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId3, threadId2).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId2, messageId3, messageId1)
  }

  @Test
  def deleteShouldRemoveOnlyTargetEntry(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_2, messageId2, threadId2).block()
    testee.save(ALICE, KEYWORD_B, DATE_3, messageId1, threadId3).block()

    testee.delete(ALICE, KEYWORD_A, DATE_1, messageId1).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId2)
    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_B, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId1)
  }

  @Test
  def deleteShouldNotRemoveEntryWhenReceivedAtDoesNotMatch(): Unit = {
    testee.save(ALICE, KEYWORD_A, DATE_1, messageId1, threadId1).block()
    testee.save(ALICE, KEYWORD_A, DATE_3, messageId2, threadId3).block()

    testee.delete(ALICE, KEYWORD_A, DATE_2, messageId1).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId2, messageId1)
  }

  @Test
  def deleteShouldBeIdempotent(): Unit = {
    assertThatCode(() => testee.delete(ALICE, KEYWORD_A, DATE_4, messageId4).block())
      .doesNotThrowAnyException()
  }

  @Test
  def saveShouldBeIdempotent(): Unit = {
    testee.save(ALICE, KEYWORD_C, DATE_5, messageId3, threadId1).block()
    testee.save(ALICE, KEYWORD_C, DATE_5, messageId3, threadId1).block()

    assertThat(SFlux.fromPublisher(testee.listMessagesByKeyword(ALICE, KEYWORD_C, Limit.limit(12), false)).collectSeq().block().asJava)
      .containsExactly(messageId3)
  }

  @Test
  def listShouldThrowOnUndefinedLimit(): Unit = {
    assertThatThrownBy(() => testee.listMessagesByKeyword(ALICE, KEYWORD_A, Limit.unlimited(), false).blockLast())
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def listSinceAfterShouldThrowOnUndefinedLimit(): Unit = {
    assertThatThrownBy(() => testee.listMessagesByKeywordSinceAfter(ALICE, KEYWORD_A, DATE_3, Limit.unlimited(), false).blockLast())
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def listBeforeShouldThrowOnUndefinedLimit(): Unit = {
    assertThatThrownBy(() => testee.listMessagesByKeywordBefore(ALICE, KEYWORD_A, DATE_3, Limit.unlimited(), false).blockLast())
      .isInstanceOf(classOf[IllegalArgumentException])
  }
}
