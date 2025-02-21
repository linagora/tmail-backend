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
package com.linagora.tmail.james.jmap.perfs;

import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.apache.james.mailbox.store.mail.AttachmentIdAssignationStrategy;

public class TMailCleverAttachmentIdAssignationStrategy implements AttachmentIdAssignationStrategy {
    @Override
    public AttachmentId assign(ParsedAttachment parsedAttachment, MessageId messageId) {
        if (parsedAttachment instanceof TMailCleverParsedAttachment TMailCleverParsedAttachment) {
            return StringBackedAttachmentId.from(TMailCleverParsedAttachment.translate(messageId));
        }
        return StringBackedAttachmentId.random();
    }
}
