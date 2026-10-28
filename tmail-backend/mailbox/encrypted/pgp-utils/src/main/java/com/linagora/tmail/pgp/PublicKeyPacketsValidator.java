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

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Walks the OpenPGP packet headers (RFC 4880 section 4.2) of a de-armored public key and rejects it
 * when a declared packet or user attribute subpacket length exceeds the bytes actually present, or when
 * it holds packets that do not belong to a transferable public key (RFC 4880 section 11.1).
 *
 * BouncyCastle sizes its buffers from these declared lengths, bounded only by the JVM max memory for
 * nested streams: a few bytes announcing a 1 GB user attribute subpacket would otherwise be allocated.
 */
class PublicKeyPacketsValidator {
    private static final int USER_ATTRIBUTE_PACKET_TAG = 17;
    // Signature, public key, trust, user ID, public subkey, user attribute, padding (RFC 9580)
    private static final Set<Integer> PUBLIC_KEY_PACKET_TAGS = Set.of(2, 6, 12, 13, 14, USER_ATTRIBUTE_PACKET_TAG, 21);

    static void validate(byte[] packets) {
        ByteBuffer buffer = ByteBuffer.wrap(packets);
        while (buffer.hasRemaining()) {
            validatePacket(buffer);
        }
    }

    private static void validatePacket(ByteBuffer buffer) {
        int header = Byte.toUnsignedInt(buffer.get());
        if ((header & 0x80) == 0) {
            throw new IllegalArgumentException("Invalid OpenPGP packet header");
        }
        boolean newFormat = (header & 0x40) != 0;
        int tag = newFormat ? header & 0x3f : (header >> 2) & 0x0f;
        if (!PUBLIC_KEY_PACKET_TAGS.contains(tag)) {
            throw new IllegalArgumentException("Unexpected OpenPGP packet tag " + tag + " in a public key");
        }
        long length = newFormat ? readNewFormatLength(buffer) : readOldFormatLength(buffer, header & 0x03);
        ByteBuffer body = slice(buffer, length);
        if (tag == USER_ATTRIBUTE_PACKET_TAG) {
            validateUserAttributeSubpackets(body);
        }
    }

    private static void validateUserAttributeSubpackets(ByteBuffer body) {
        while (body.hasRemaining()) {
            long length = readNewFormatLength(body);
            if (length < 1) {
                throw new IllegalArgumentException("Invalid OpenPGP user attribute subpacket length");
            }
            slice(body, length);
        }
    }

    private static long readOldFormatLength(ByteBuffer buffer, int lengthType) {
        return switch (lengthType) {
            case 0 -> readUnsigned(buffer, 1);
            case 1 -> readUnsigned(buffer, 2);
            case 2 -> readUnsigned(buffer, 4);
            // Indeterminate lengths are only meant for data streams, never within a key
            default -> throw new IllegalArgumentException("Unexpected indeterminate OpenPGP packet length");
        };
    }

    private static long readNewFormatLength(ByteBuffer buffer) {
        int first = (int) readUnsigned(buffer, 1);
        if (first < 192) {
            return first;
        }
        if (first < 224) {
            return ((first - 192) << 8) + readUnsigned(buffer, 1) + 192;
        }
        if (first == 255) {
            return readUnsigned(buffer, 4);
        }
        // Partial body lengths are only allowed for data packets, never within a key
        throw new IllegalArgumentException("Unexpected partial OpenPGP packet length");
    }

    private static long readUnsigned(ByteBuffer buffer, int octets) {
        requireRemaining(buffer, octets);
        long value = 0;
        for (int i = 0; i < octets; i++) {
            value = (value << 8) | Byte.toUnsignedInt(buffer.get());
        }
        return value;
    }

    private static ByteBuffer slice(ByteBuffer buffer, long length) {
        requireRemaining(buffer, length);
        ByteBuffer slice = buffer.slice(buffer.position(), (int) length);
        buffer.position(buffer.position() + (int) length);
        return slice;
    }

    private static void requireRemaining(ByteBuffer buffer, long length) {
        if (length > buffer.remaining()) {
            throw new IllegalArgumentException("OpenPGP length " + length + " exceeds the " + buffer.remaining() + " remaining bytes");
        }
    }
}
