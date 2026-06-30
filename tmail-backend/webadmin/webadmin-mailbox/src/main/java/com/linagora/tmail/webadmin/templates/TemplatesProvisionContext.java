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

package com.linagora.tmail.webadmin.templates;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.ImmutableList;

public class TemplatesProvisionContext {
    public record Snapshot(long processedUsers,
                           long appliedTemplates,
                           long skippedTemplates,
                           long removedTemplates,
                           ImmutableList<String> failedUsers) {
    }

    private final AtomicLong processedUsers;
    private final AtomicLong appliedTemplates;
    private final AtomicLong skippedTemplates;
    private final AtomicLong removedTemplates;
    private final ConcurrentLinkedDeque<String> failedUsers;

    public TemplatesProvisionContext() {
        this.processedUsers = new AtomicLong();
        this.appliedTemplates = new AtomicLong();
        this.skippedTemplates = new AtomicLong();
        this.removedTemplates = new AtomicLong();
        this.failedUsers = new ConcurrentLinkedDeque<>();
    }

    public void incrementProcessedUsers() {
        processedUsers.incrementAndGet();
    }

    public void incrementAppliedTemplates() {
        appliedTemplates.incrementAndGet();
    }

    public void incrementSkippedTemplates() {
        skippedTemplates.incrementAndGet();
    }

    public void incrementRemovedTemplates(long amount) {
        removedTemplates.addAndGet(amount);
    }

    public void addToFailedUsers(String user) {
        failedUsers.add(user);
    }

    public Snapshot snapshot() {
        return new Snapshot(processedUsers.get(),
            appliedTemplates.get(),
            skippedTemplates.get(),
            removedTemplates.get(),
            ImmutableList.copyOf(failedUsers));
    }
}
