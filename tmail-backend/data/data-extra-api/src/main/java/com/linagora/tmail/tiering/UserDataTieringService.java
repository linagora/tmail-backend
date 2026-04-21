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

import java.time.Duration;

import org.apache.james.core.Username;

import reactor.core.publisher.Mono;

public interface UserDataTieringService {
    /**
     * Tiers user data by:
     * - Clearing the JMAP /changes projection (email and mailbox changes)
     * - Clearing the thread-guessing table for the user
     * - For messages older than {@code tiering}, clearing their fast-view projection,
     *   clearing their attachments
     *   and evicting their header blobs from the blob-store cache
     *
     * Per-message successes and failures are reported to {@code context}.
     */
    Mono<Void> tierUserData(Username username, Duration tiering, UserDataTieringContext context, int messagesPerSecond);
}
