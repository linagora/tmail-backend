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

package com.linagora.tmail.blob.bucket;

import org.apache.commons.configuration2.Configuration;

/**
 * Whether the buckets Twake Mail writes to are provisioned at startup.
 *
 * <p>Read from <code>blob.properties</code>:</p>
 *
 * <pre>
 * buckets.startup.check=true
 * </pre>
 *
 * <p>Disabling it falls back to the object storage creating buckets lazily, upon the first write.</p>
 */
public record BucketsStartUpCheckConfiguration(boolean enabled) {
    public static final BucketsStartUpCheckConfiguration ENABLED = new BucketsStartUpCheckConfiguration(true);
    public static final BucketsStartUpCheckConfiguration DISABLED = new BucketsStartUpCheckConfiguration(false);

    static final String ENABLED_PROPERTY = "buckets.startup.check";

    public static BucketsStartUpCheckConfiguration from(Configuration configuration) {
        if (configuration.getBoolean(ENABLED_PROPERTY, true)) {
            return ENABLED;
        }
        return DISABLED;
    }
}
