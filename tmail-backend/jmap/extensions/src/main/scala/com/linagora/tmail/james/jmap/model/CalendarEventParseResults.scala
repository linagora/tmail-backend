package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.model.CalendarEventParse.UnparsedBlobId
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.mail.BlobId

object CalendarEventParseResults {
  def notFound(blobId: UnparsedBlobId): CalendarEventParseResults = CalendarEventParseResults(None, Some(CalendarEventNotFound(Set(blobId))), None)

  def notFound(blobId: BlobId): CalendarEventParseResults = CalendarEventParseResults(None, Some(CalendarEventNotFound(Set(blobId.value))), None)

  def notParse(blobId: BlobId): CalendarEventParseResults = CalendarEventParseResults(None, None, Some(CalendarEventNotParsable(Set(blobId.value))))

  def parse(blobId: BlobId, calendarEventParsed: List[CalendarEventParsed]): CalendarEventParseResults = CalendarEventParseResults(Some(Map(blobId -> CalendarEventParsedList(calendarEventParsed))), None, None)

  def empty(): CalendarEventParseResults = CalendarEventParseResults(None, None, None)

  def merge(response1: CalendarEventParseResults, response2: CalendarEventParseResults): CalendarEventParseResults = CalendarEventParseResults(
    parsed = (response1.parsed ++ response2.parsed).reduceOption((parsed1, parsed2) => parsed1 ++ parsed2),
    notFound = (response1.notFound ++ response2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)),
    notParsable = (response1.notParsable ++ response2.notParsable).reduceOption((notParsable1, notParsable2) => notParsable1.merge(notParsable2)))
}

case class CalendarEventParseResults(parsed: Option[Map[BlobId, CalendarEventParsedList]],
                                     notFound: Option[CalendarEventNotFound],
                                     notParsable: Option[CalendarEventNotParsable]) {
  def asResponse(accountId: AccountId): CalendarEventParseResponse = CalendarEventParseResponse(accountId, parsed, notFound, notParsable)
}
