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

public class DavClientException extends RuntimeException {
    private final boolean shouldRetry;

    public DavClientException(String message) {
        this(message, false);
    }

    public DavClientException(String message, boolean shouldRetry) {
        super(message);
        this.shouldRetry = shouldRetry;
    }

    public DavClientException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public DavClientException(String message, Throwable cause, boolean shouldRetry) {
        super(message, cause);
        this.shouldRetry = shouldRetry;
    }

    public boolean shouldRetry() {
        return shouldRetry;
    }
}
