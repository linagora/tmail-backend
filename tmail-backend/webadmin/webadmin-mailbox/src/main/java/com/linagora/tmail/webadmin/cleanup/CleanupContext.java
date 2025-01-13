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

package com.linagora.tmail.webadmin.cleanup;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class CleanupContext {
     record Snapshot(long processedUsersCount, long deletedMessagesCount, ImmutableList<String> failedUsers) {
        @Override
        public boolean equals(Object o) {
            if (o instanceof Snapshot) {
                Snapshot that = (Snapshot) o;

                return Objects.equals(this.processedUsersCount, that.processedUsersCount)
                    && Objects.equals(this.deletedMessagesCount, that.deletedMessagesCount)
                    && Objects.equals(this.failedUsers, that.failedUsers);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(processedUsersCount, deletedMessagesCount, failedUsers);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("processedUsersCount", processedUsersCount)
                .add("deletedMessagesCount", deletedMessagesCount)
                .add("failedUsers", failedUsers)
                .toString();
        }
    }

    private final AtomicLong processedUsersCount;
    private final AtomicLong deletedMessagesCount;
    private final ConcurrentLinkedDeque<String> failedUsers;

    public CleanupContext() {
        this.processedUsersCount = new AtomicLong();
        this.deletedMessagesCount = new AtomicLong();
        this.failedUsers = new ConcurrentLinkedDeque<>();
    }

    public void incrementProcessed() {
        processedUsersCount.incrementAndGet();
    }

    public void incrementDeletedMessagesCount(long amount) {
        deletedMessagesCount.addAndGet(amount);
    }

    public void addToFailedUsers(String user) {
        failedUsers.add(user);
    }

    public Snapshot snapshot() {
        return new Snapshot(processedUsersCount.get(),
            deletedMessagesCount.get(),
            ImmutableList.copyOf(failedUsers));
    }
}
