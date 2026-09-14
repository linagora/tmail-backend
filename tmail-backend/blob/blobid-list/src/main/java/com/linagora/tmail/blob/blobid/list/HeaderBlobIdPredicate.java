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

package com.linagora.tmail.blob.blobid.list;

import java.util.function.Predicate;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdEntropy;
import org.apache.james.mailbox.cassandra.mail.ContentRecoveryMessageContentSaver;
import org.apache.james.server.blob.deduplication.GenerationAware;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;

/**
 * Tells the header blobs apart from the content addressed blobs the {@link BlobIdList} exists for.
 *
 * <p>{@link ContentRecoveryMessageContentSaver} writes headers under a random id carrying the
 * {@code _hdr} suffix: such an id is unique by construction, is never recomputed, and would thus only
 * ever add a row no later save can hit. Bodies and attachments, being content addressed, are the ones a
 * second save actually collides with, and the list is kept for them.</p>
 *
 * <p>The suffix alone does not spot a header: the encoding of an id spells out {@code _}, {@code h},
 * {@code d} and {@code r} like any other characters, so a content addressed id can end with those four.
 * What sets a header apart is that its suffix sits on top of a whole payload, so that once the family
 * and generation prefix is stripped it is longer than the encoding of the configured entropy, which is
 * all a content addressed id ever is.</p>
 */
public class HeaderBlobIdPredicate implements Predicate<BlobId> {
    /** What closes the family and the generation: {@code _} for a generation aware id, {@code /} for its MinIO flavour. */
    private static final String GENERATION_SEPARATORS = "_/";
    /** The MinIO flavour also spreads the first two characters of the payload into as many folders. */
    private static final int MINIO_FOLDER_SEPARATORS = 2;
    private static final int NONE = -1;

    private final int contentAddressedPayloadLength;

    public HeaderBlobIdPredicate(BlobId.Factory blobIdFactory) {
        this.contentAddressedPayloadLength = blobIdFactory.encoding()
            .encode(new byte[BlobIdEntropy.entropyBytes()])
            .length();
    }

    @Override
    public boolean test(BlobId blobId) {
        return blobId.asString().endsWith(ContentRecoveryMessageContentSaver.HEADER_BLOB_ID_SUFFIX)
            && payloadLengthOf(blobId) > contentAddressedPayloadLength;
    }

    /**
     * A generation aware id spells out a {@code family_generation_} prefix, and its MinIO flavour further spreads the
     * first two characters of the payload into folders: neither is payload. Anything else is, slashes included, as
     * standard Base64 spells them out.
     */
    private static int payloadLengthOf(BlobId blobId) {
        String id = blobId.asString();
        if (blobId instanceof MinIOGenerationAwareBlobId) {
            return withoutGenerationPrefix(id).length() - MINIO_FOLDER_SEPARATORS;
        }
        if (blobId instanceof GenerationAware) {
            return withoutGenerationPrefix(id).length();
        }
        return id.length();
    }

    /**
     * Drops the {@code family_generation_} a generation aware id is prefixed with, leaving one that carries no such
     * prefix untouched.
     */
    private static String withoutGenerationPrefix(String id) {
        int afterFamily = separatorClosingNumberAt(id, 0);
        if (afterFamily == NONE) {
            return id;
        }
        int afterGeneration = separatorClosingNumberAt(id, afterFamily + 1);
        if (afterGeneration == NONE) {
            return id;
        }
        return id.substring(afterGeneration + 1);
    }

    /**
     * The index of the separator closing the non empty run of digits starting at {@code from}, or {@link #NONE} when
     * no such run is closed there.
     */
    private static int separatorClosingNumberAt(String id, int from) {
        int index = from;
        while (index < id.length() && Character.isDigit(id.charAt(index))) {
            index++;
        }
        boolean closesANumber = index > from
            && index < id.length()
            && GENERATION_SEPARATORS.indexOf(id.charAt(index)) != NONE;

        if (closesANumber) {
            return index;
        }
        return NONE;
    }
}
