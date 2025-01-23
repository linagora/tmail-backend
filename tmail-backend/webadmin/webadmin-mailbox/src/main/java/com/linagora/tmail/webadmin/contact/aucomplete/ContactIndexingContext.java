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

package com.linagora.tmail.webadmin.contact.aucomplete;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.ImmutableList;

public class ContactIndexingContext {

    public record Snapshot(long processedUsersCount, long indexedContactsCount,
                           long failedContactsCount, ImmutableList<String> failedUsers) {
    }

    private final AtomicLong processedUsersCount;
    private final AtomicLong indexedContactsCount;
    private final AtomicLong failedContactsCount;
    private final ConcurrentLinkedDeque<String> failedUsers;

    public ContactIndexingContext() {
        this.processedUsersCount = new AtomicLong(0);
        this.indexedContactsCount = new AtomicLong(0);
        this.failedUsers = new ConcurrentLinkedDeque<>();
        this.failedContactsCount = new AtomicLong(0);
    }

    public void increaseProcessedUsersCount() {
        processedUsersCount.incrementAndGet();
    }

    public void increaseIndexedContactsCount() {
        indexedContactsCount.incrementAndGet();
    }

    public void addToFailedUsers(String user) {
        failedUsers.add(user);
    }

    public void increaseFailedContactsCount() {
        failedContactsCount.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(processedUsersCount.get(),
            indexedContactsCount.get(),
            failedContactsCount.get(),
            ImmutableList.copyOf(failedUsers));
    }
}
