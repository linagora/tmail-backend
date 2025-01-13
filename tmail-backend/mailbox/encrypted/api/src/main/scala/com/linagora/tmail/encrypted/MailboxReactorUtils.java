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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.encrypted;

import java.util.Optional;

import org.apache.james.mailbox.exception.MailboxException;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public abstract class MailboxReactorUtils {
    public static <T> T block(Mono<T> publisher) throws MailboxException {
        try {
            return publisher.block();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MailboxException mailboxException) {
                throw mailboxException;
            }

            throw e;
        }
    }

    public static <T> T block(Publisher<T> publisher) throws MailboxException {
        return block(Mono.from(publisher));
    }

    public static <T> Optional<T> blockOptional(Mono<T> publisher) throws MailboxException {
        try {
            return publisher.blockOptional();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MailboxException mailboxException) {
                throw mailboxException;
            }

            throw e;
        }
    }
}
