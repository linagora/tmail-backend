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

package com.linagora.tmail.dav;

import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MessageResult;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.linagora.tmail.james.jmap.model.CalendarUidField;

public record DavUid(String value) {
    public static final String X_MEETING_UID_HEADER = "X-MEETING-UID";

    public static DavUid fromCalendarUidField(CalendarUidField calendarUidField) {
        return new DavUid(calendarUidField.value());
    }

    public static DavUid fromMessageHeaders(MessageResult messageResult) {
        try {
            Iterator<Header> headers = messageResult.getHeaders().headers();
            return Iterators.tryFind(headers, header -> header.getName().equals(X_MEETING_UID_HEADER)).toJavaUtil()
                .map(Header::getValue)
                .map(DavUid::new)
                .orElseThrow(() -> new RuntimeException("Unable to retrieve X_MEETING_UID_HEADER (VEVENT uid) from message with id '%s'".formatted(messageResult.getMessageId().serialize())));
        } catch (Exception e) {
            throw new RuntimeException("Failed reading messageResult headers", e);
        }
    }

    public DavUid {
        Preconditions.checkArgument(StringUtils.isNotEmpty(value), "DAV uid should not be empty");
    }
}
