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

package com.linagora.tmail.tiering;

import java.util.concurrent.atomic.AtomicLong;

public class UserDataTieringContext {

    public record Snapshot(long tieredMessageCount, long failedMessageCount) {}

    private final AtomicLong tieredMessageCount = new AtomicLong();
    private final AtomicLong failedMessageCount = new AtomicLong();

    public void incrementTiered() {
        tieredMessageCount.incrementAndGet();
    }

    public void incrementFailed() {
        failedMessageCount.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(tieredMessageCount.get(), failedMessageCount.get());
    }
}
