package com.linagora.tmail.james.common.calendar.getattendance

import com.linagora.tmail.james.common.calendar.getattendance.CalDavLinagoraCalendarEventAttendanceGetMethodContract.CALMLY_AWAIT
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture.{ALICE, ALICE_PASSWORD, BOB}
import org.apache.james.mailbox.model.{MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.SMTPMessageSender
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.BeforeEach

import java.util.Optional
import java.util.concurrent.TimeUnit


object CalDavLinagoraCalendarEventAttendanceGetMethodContract {
  private lazy val CALMLY_AWAIT: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
}

abstract class CalDavLinagoraCalendarEventAttendanceGetMethodContract extends LinagoraCalendarEventAttendanceGetMethodContract {

  @BeforeEach
  override def setUp(server: GuiceJamesServer): Unit = {
    super.setUp(server)
  }

  override def _sendInvitationEmailToBobAndGetIcsBlobIds(server: GuiceJamesServer, invitationEml: String, icsPartIds: String*): Seq[String] = {
    def searchBobInboxForNewMessages(): Optional[MessageId] = {
      server.getProbe(classOf[MailboxProbeImpl])
        .searchMessage(
          MultimailboxesSearchQuery.from(
            SearchQuery.of(SearchQuery.all)).build, BOB.asString(), 1).stream().findAny()
    }

    val mail = ClassLoaderUtils.getSystemResourceAsString(invitationEml)

    new SMTPMessageSender(ALICE.getDomainPart.get().asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(ALICE.asString(), ALICE_PASSWORD)
      .sendMessageWithHeaders(ALICE.asString(), BOB.asString(), mail)

    CALMLY_AWAIT.atMost(10, TimeUnit.SECONDS)
      .dontCatchUncaughtExceptions()
      .until(() => searchBobInboxForNewMessages().isPresent)

    val messageId = searchBobInboxForNewMessages().get()

    icsPartIds.map(partId => s"${messageId.serialize()}_$partId")
  }
}