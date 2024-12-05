package com.linagora.tmail.james.jmap;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

interface EventAttendanceRepository {
   Publisher<AttendanceStatus> getAttendanceStatus(Username username, MessageId messageId);
   
   Publisher<Void> setAttendanceStatus(Username username, MessageId messageId, AttendanceStatus attendanceStatus);
}