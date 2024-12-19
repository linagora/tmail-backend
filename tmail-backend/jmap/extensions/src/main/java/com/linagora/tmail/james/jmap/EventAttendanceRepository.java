package com.linagora.tmail.james.jmap;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.BlobIds;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults;
import com.linagora.tmail.james.jmap.model.LanguageLocation;

public interface EventAttendanceRepository {
   Publisher<AttendanceStatus> getAttendanceStatus(Username username, MessageId messageId);
   
   Publisher<CalendarEventReplyResults> setAttendanceStatus(Username username, AttendanceStatus attendanceStatus,
                                                            BlobIds eventBlobIds,
                                                            Optional<LanguageLocation> maybePreferredLanguage);
}