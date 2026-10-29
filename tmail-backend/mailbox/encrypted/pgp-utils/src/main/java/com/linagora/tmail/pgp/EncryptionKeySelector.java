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

package com.linagora.tmail.pgp;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import org.bouncycastle.bcpg.SignatureSubpacketTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;

/**
 * Selects the key of a ring to encrypt to, following RFC 9580 section 5.2.3.29: a key, primary or subkey,
 * that is encryption capable, flagged for encryption by its self or binding signature, and neither expired
 * nor revoked (nor its primary key). Subkeys are preferred, then the most recent ones.
 */
class EncryptionKeySelector {
    record Selection(PGPPublicKey primaryKey, PGPPublicKey encryptionKey) {
    }

    private static final int ENCRYPTION_FLAGS = KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE;
    private static final ImmutableSet<Integer> PRIMARY_KEY_SELF_SIGNATURE_TYPES = ImmutableSet.of(
        PGPSignature.DIRECT_KEY,
        PGPSignature.DEFAULT_CERTIFICATION,
        PGPSignature.NO_CERTIFICATION,
        PGPSignature.CASUAL_CERTIFICATION,
        PGPSignature.POSITIVE_CERTIFICATION);
    private static final ImmutableSet<Integer> SUBKEY_SELF_SIGNATURE_TYPES = ImmutableSet.of(PGPSignature.SUBKEY_BINDING);
    private static final Comparator<PGPPublicKey> PREFERRED_FIRST = Comparator.comparing(PGPPublicKey::isMasterKey)
        .thenComparing(PGPPublicKey::getCreationTime, Comparator.reverseOrder());

    static Selection select(PGPPublicKeyRingCollection keyRings, Instant now) {
        return Streams.stream(keyRings.getKeyRings())
            .flatMap(keyRing -> select(keyRing, now).stream())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Can't find encryption key in key ring."));
    }

    private static Optional<Selection> select(PGPPublicKeyRing keyRing, Instant now) {
        PGPPublicKey primaryKey = keyRing.getPublicKey();
        if (!isValid(primaryKey, now)) {
            return Optional.empty();
        }
        return Streams.stream(keyRing.getPublicKeys())
            .filter(key -> isUsableForEncryption(primaryKey, key, now))
            .min(PREFERRED_FIRST)
            .map(encryptionKey -> new Selection(primaryKey, encryptionKey));
    }

    private static boolean isUsableForEncryption(PGPPublicKey primaryKey, PGPPublicKey key, Instant now) {
        return key.isEncryptionKey()
            && isValid(key, now)
            && hasEncryptionFlag(primaryKey, key);
    }

    private static boolean isValid(PGPPublicKey key, Instant now) {
        return !key.hasRevocation() && !isExpired(key, now);
    }

    private static boolean isExpired(PGPPublicKey key, Instant now) {
        long validSeconds = key.getValidSeconds();
        return validSeconds > 0 && key.getCreationTime().toInstant().plusSeconds(validSeconds).isBefore(now);
    }

    private static boolean hasEncryptionFlag(PGPPublicKey primaryKey, PGPPublicKey key) {
        return selfSignatures(primaryKey, key)
            .filter(signature -> signature.getHashedSubPackets() != null)
            .filter(signature -> signature.getHashedSubPackets().hasSubpacket(SignatureSubpacketTags.KEY_FLAGS))
            .max(Comparator.comparing(PGPSignature::getCreationTime))
            .map(signature -> (signature.getHashedSubPackets().getKeyFlags() & ENCRYPTION_FLAGS) != 0)
            .orElse(false);
    }

    private static Stream<PGPSignature> selfSignatures(PGPPublicKey primaryKey, PGPPublicKey key) {
        ImmutableSet<Integer> selfSignatureTypes = key.isMasterKey() ? PRIMARY_KEY_SELF_SIGNATURE_TYPES : SUBKEY_SELF_SIGNATURE_TYPES;
        return Streams.stream(key.getSignatures())
            .filter(signature -> selfSignatureTypes.contains(signature.getSignatureType()))
            .filter(signature -> signature.getKeyID() == primaryKey.getKeyID());
    }
}
