package com.linagora.tmail.james.jmap;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.BlobIds;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.method.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventReplyResults;
import com.linagora.tmail.james.jmap.model.LanguageLocation;

public interface EventAttendanceRepository {
   Publisher<CalendarEventAttendanceResults> getAttendanceStatus(Username username, BlobIds calendarEventBlobIds);
   
   Publisher<CalendarEventReplyResults> setAttendanceStatus(Username username, AttendanceStatus attendanceStatus,
                                                            BlobIds eventBlobIds,
                                                            Optional<LanguageLocation> maybePreferredLanguage);
}