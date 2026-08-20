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

package com.linagora.tmail.blob.mail;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.blob.api.BucketName;

/**
 * Whether mails in transit - stored by the mail queue and by the mail repositories - are deduplicated with
 * the copies eventually stored within the mailboxes, and if not, in which bucket they are to be stored.
 *
 * Read from <code>blob.properties</code>:
 *
 * <pre>
 * mailprocessing.deduplication.enabled=true
 * mailprocessing.bucket=mail-processing
 * </pre>
 *
 * @see DuplicatingMimeMessageStore
 */
public record MailProcessingConfiguration(boolean deduplicationEnabled, BucketName mailProcessingBucket) {
    public static final BucketName MAIL_PROCESSING_BUCKET = BucketName.of("mail-processing");
    public static final MailProcessingConfiguration DEDUPLICATED = new MailProcessingConfiguration(true, MAIL_PROCESSING_BUCKET);

    static final String DEDUPLICATION_ENABLED_PROPERTY = "mailprocessing.deduplication.enabled";
    // Accepted alias, consistent with the `deduplication.enable` spelling of the blob store deduplication
    static final String DEDUPLICATION_ENABLE_PROPERTY = "mailprocessing.deduplication.enable";
    static final String BUCKET_PROPERTY = "mailprocessing.bucket";

    public static MailProcessingConfiguration duplicated() {
        return duplicated(MAIL_PROCESSING_BUCKET);
    }

    public static MailProcessingConfiguration duplicated(BucketName mailProcessingBucket) {
        return new MailProcessingConfiguration(false, mailProcessingBucket);
    }

    public static MailProcessingConfiguration from(Configuration configuration) {
        boolean deduplicationEnabled = configuration.getBoolean(DEDUPLICATION_ENABLED_PROPERTY,
            configuration.getBoolean(DEDUPLICATION_ENABLE_PROPERTY, true));

        if (deduplicationEnabled) {
            return DEDUPLICATED;
        }
        return duplicated(BucketName.of(configuration.getString(BUCKET_PROPERTY, MAIL_PROCESSING_BUCKET.asString())));
    }
}
