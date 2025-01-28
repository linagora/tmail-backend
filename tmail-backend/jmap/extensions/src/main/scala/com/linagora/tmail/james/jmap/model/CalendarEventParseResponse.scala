package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.mail.BlobId

case class CalendarEventParseResponse(accountId: AccountId,
                                      parsed: Option[Map[BlobId, CalendarEventParsedList]],
                                      notFound: Option[CalendarEventNotFound],
                                      notParsable: Option[CalendarEventNotParsable])