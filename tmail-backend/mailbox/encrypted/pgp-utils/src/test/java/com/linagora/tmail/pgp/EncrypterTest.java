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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.message.SingleBodyBuilder;
import org.apache.james.mime4j.stream.NameValuePair;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Bytes;

class EncrypterTest {
    public static final String INPUT = "ga bou zo meuh\r\nBienvenue chez les shadocks.\r\n";

    private static final int SIGN_FLAGS = KeyFlags.CERTIFY_OTHER | KeyFlags.SIGN_DATA;
    private static final int ENCRYPT_FLAGS = KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE;
    private static final long NO_EXPIRATION = 0L;
    private static final long ONE_HOUR = Duration.ofHours(1).toSeconds();
    private static final long ONE_DAY = Duration.ofDays(1).toSeconds();
    private static final Instant NOW = Instant.now();
    private static final Instant YESTERDAY = NOW.minus(Duration.ofDays(1));
    private static final Instant LAST_WEEK = NOW.minus(Duration.ofDays(7));

    @BeforeAll
    static void setUpAll() throws Exception {
        String bouncyCastleProviderClassName = "org.bouncycastle.jce.provider.BouncyCastleProvider";
        Security.addProvider((Provider)Class.forName(bouncyCastleProviderClassName).getDeclaredConstructor().newInstance());
    }

    @Test
    void encryptEmptyDataShouldBeSupported() throws Exception {
        byte[] keyBytes1 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Encrypter.forKeys(keyBytes1)
            .encrypt(ByteSource.wrap("".getBytes(StandardCharsets.UTF_8)), out);

        byte[] decryptedPayload1 = Decrypter.forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(out.toByteArray()))
            .readAllBytes();

        assertThat(new String(decryptedPayload1, StandardCharsets.UTF_8))
            .isEqualTo("");
    }

    @Test
    void encryptShouldProduceValidGPGData() throws Exception {
        byte[] keyBytes1 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Encrypter.forKeys(keyBytes1)
            .encrypt(ByteSource.wrap(INPUT.getBytes(StandardCharsets.UTF_8)), out);

        byte[] decryptedPayload1 = Decrypter.forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(out.toByteArray()))
            .readAllBytes();

        assertThat(new String(decryptedPayload1, StandardCharsets.UTF_8))
            .isEqualTo(INPUT);
    }

    @Test
    void encryptShouldWorkWithMultipleKeys() throws Exception {
        byte[] keyBytes1 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        byte[] keyBytes2 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg2.pub").readAllBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Encrypter.forKeys(keyBytes1, keyBytes2)
            .encrypt(ByteSource.wrap(INPUT.getBytes(StandardCharsets.UTF_8)), out);

        byte[] decryptedPayload = Decrypter.forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(out.toByteArray()))
            .readAllBytes();

        assertThat(new String(decryptedPayload, StandardCharsets.UTF_8))
            .isEqualTo(INPUT);
    }

    @Test
    void readPublicKeyShouldThrowOnBadFormat() {
        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream("bad".getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readPublicKeyShouldRejectAnAbsurdUserAttributeSubpacketLength() {
        byte[] payload = BaseEncoding.base64().decode("j4/R4v8+QI/R//8AAQI=");

        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream(payload)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readPublicKeyShouldRejectAnArmoredAbsurdUserAttributeSubpacketLength() {
        String armoredKey = """
            -----BEGIN PGP PUBLIC KEY BLOCK-----

            j4/R4v8+QI/R//8AAQI=
            -----END PGP PUBLIC KEY BLOCK-----
            """;

        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream(armoredKey.getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readPublicKeyShouldRejectAUserAttributeSubpacketLengthExceedingThePacket() {
        // New format user attribute packet (tag 17) whose subpacket announces 0x3e408fd1 bytes
        byte[] payload = {(byte) 0xd1, 0x05, (byte) 0xff, 0x3e, 0x40, (byte) 0x8f, (byte) 0xd1};

        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream(payload)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1044418513");
    }

    @Test
    void readPublicKeyShouldRejectAPacketLengthExceedingThePayload() {
        // New format public key packet (tag 6) announcing 0x7fffffff bytes
        byte[] payload = {(byte) 0xc6, (byte) 0xff, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x04};

        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream(payload)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readPublicKeyShouldRejectTooBigKeys() {
        byte[] payload = new byte[2 * 1024 * 1024];
        payload[0] = (byte) 0x99;

        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream(payload)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readPublicKeyShouldAcceptValidKeys() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();

        assertThat(Encrypter.readPublicKey(new ByteArrayInputStream(keyBytes)).isEncryptionKey())
            .isTrue();
    }

    @Test
    void readPublicKeyShouldIgnoreMarkerPackets() throws Exception {
        // Old format marker packet (tag 10) holding "PGP", to be ignored by readers (RFC 4880 section 5.8)
        byte[] markerPacket = {(byte) 0xa8, 0x03, 0x50, 0x47, 0x50};
        byte[] keyPackets = PGPUtil.getDecoderStream(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub")).readAllBytes();

        assertThat(Encrypter.readPublicKey(new ByteArrayInputStream(Bytes.concat(markerPacket, keyPackets))).isEncryptionKey())
            .isTrue();
    }

    @Test
    void readPublicKeyShouldSelectTheEncryptionSubkey() throws Exception {
        PGPKeyPair ecdsaPair = ecKeyPair(PublicKeyAlgorithmTags.ECDSA, NOW);
        PGPKeyPair ecdhPair = ecKeyPair(PublicKeyAlgorithmTags.ECDH, NOW);
        byte[] keyRing = keyRing(ecdsaPair, flags(SIGN_FLAGS, NO_EXPIRATION), new Subkey(ecdhPair, flags(ENCRYPT_FLAGS, NO_EXPIRATION)));

        assertThat(Encrypter.readPublicKey(new ByteArrayInputStream(keyRing)).getKeyID())
            .isEqualTo(ecdhPair.getKeyID());
    }

    @Test
    void readPublicKeyShouldNotSelectASignOnlyPrimaryKey() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();

        assertThat(Encrypter.readPublicKey(new ByteArrayInputStream(keyBytes)).isMasterKey())
            .isFalse();
    }

    @Test
    void readPublicKeyShouldRejectAKeyRingWithoutEncryptionFlag() throws Exception {
        byte[] keyRing = keyRing(ecKeyPair(PublicKeyAlgorithmTags.ECDSA, NOW), flags(SIGN_FLAGS, NO_EXPIRATION),
            new Subkey(ecKeyPair(PublicKeyAlgorithmTags.ECDH, NOW), flags(SIGN_FLAGS, NO_EXPIRATION)));

        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream(keyRing)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readPublicKeyShouldRejectAnExpiredEncryptionSubkey() throws Exception {
        byte[] keyRing = keyRing(ecKeyPair(PublicKeyAlgorithmTags.ECDSA, LAST_WEEK), flags(SIGN_FLAGS, NO_EXPIRATION),
            new Subkey(ecKeyPair(PublicKeyAlgorithmTags.ECDH, LAST_WEEK), flags(ENCRYPT_FLAGS, ONE_DAY)));

        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream(keyRing)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readPublicKeyShouldRejectAnExpiredPrimaryKey() throws Exception {
        byte[] keyRing = keyRing(ecKeyPair(PublicKeyAlgorithmTags.ECDSA, LAST_WEEK), flags(SIGN_FLAGS, ONE_DAY),
            new Subkey(ecKeyPair(PublicKeyAlgorithmTags.ECDH, LAST_WEEK), flags(ENCRYPT_FLAGS, NO_EXPIRATION)));

        assertThatThrownBy(() -> Encrypter.readPublicKey(new ByteArrayInputStream(keyRing)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readPublicKeyShouldSkipAnExpiredEncryptionSubkey() throws Exception {
        PGPKeyPair validSubkey = ecKeyPair(PublicKeyAlgorithmTags.ECDH, LAST_WEEK);
        byte[] keyRing = keyRing(ecKeyPair(PublicKeyAlgorithmTags.ECDSA, LAST_WEEK), flags(SIGN_FLAGS, NO_EXPIRATION),
            new Subkey(validSubkey, flags(ENCRYPT_FLAGS, NO_EXPIRATION)),
            new Subkey(ecKeyPair(PublicKeyAlgorithmTags.ECDH, YESTERDAY), flags(ENCRYPT_FLAGS, ONE_HOUR)));

        assertThat(Encrypter.readPublicKey(new ByteArrayInputStream(keyRing)).getKeyID())
            .isEqualTo(validSubkey.getKeyID());
    }

    @Test
    void readFingerprintShouldReturnThePrimaryKeyFingerprint() throws Exception {
        byte[] keyBytes = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();

        assertThat(BaseEncoding.base16().encode(Encrypter.readFingerprint(new ByteArrayInputStream(keyBytes))))
            .isEqualTo("3BA423385C8C80D453D7E6F95BF4E866E9CC6FA2");
    }

    @Test
    void decryptWithIncorrectKeyShouldFail() throws Exception {
        byte[] keyBytes1 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Encrypter.forKeys(keyBytes1)
            .encrypt(ByteSource.wrap(INPUT.getBytes(StandardCharsets.UTF_8)), out);

        assertThatThrownBy(() -> Decrypter.forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg2.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(out.toByteArray())))
            .isInstanceOf(Exception.class);
    }

    @Test
    void encryptMessageShouldMatchRFC3156MimeStructure() throws Exception {
        Message clearMessage = Message.Builder.of()
            .setSubject("small message")
            .setBody("small message has size less than one MB", StandardCharsets.UTF_8)
            .build();

        byte[] keyBytes1 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();

        Message encryptedMessage = Encrypter.forKeys(keyBytes1)
            .encrypt(clearMessage);

        DefaultMessageWriter defaultMessageWriter = new DefaultMessageWriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        defaultMessageWriter.writeMessage(encryptedMessage, out);

        String encryptedMessageAsString = out.toString(StandardCharsets.UTF_8);
        assertThat(encryptedMessageAsString)
            .startsWith("""
                MIME-Version: 1.0\r
                Subject: small message\r
                Content-Type: multipart/encrypted; protocol="application/pgp-encrypted";\r
                 boundary=\"""");
        assertThat(encryptedMessageAsString).contains("""
            Content-Type: application/pgp-encrypted\r
            \r
            Version: 1\r
            """);
        assertThat(encryptedMessageAsString)
            .contains("Content-Type: application/octet-stream", "-----BEGIN PGP MESSAGE-----");
    }

    @Test
    void encryptMessagePayloadShouldBeDecryptable() throws Exception {
        Message clearMessage = Message.Builder.of()
            .setSubject("small message")
            .setBody(MultipartBuilder.create("report")
                .addContentTypeParameter(new NameValuePair("boundary", "-=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=-"))
                .addTextPart("first", StandardCharsets.UTF_8)
                .addBodyPart(BodyPartBuilder
                    .create()
                    .setBody(SingleBodyBuilder.create()
                        .setText("Final-Recipient: rfc822; final_recipient")
                        .buildText())
                    .setContentType("message/disposition-notification")
                    .build())
                .build())
            .setField(Fields.contentType("multipart/report", new NameValuePair("boundary", "-=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=-")))
            .build();

        byte[] keyBytes1 = ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.pub").readAllBytes();

        Message encryptedMessage = Encrypter.forKeys(keyBytes1)
            .encrypt(clearMessage);

        Body encryptedMessageBody = encryptedMessage.getBody();
        Multipart encryptedMultiPart = (Multipart) encryptedMessageBody;
        Body encryptedBodyPart = encryptedMultiPart.getBodyParts().get(1).getBody();
        ByteArrayOutputStream encryptedBodyBytes = new ByteArrayOutputStream();
        new DefaultMessageWriter().writeBody(encryptedBodyPart, encryptedBodyBytes);

        byte[] decryptedPayload = Decrypter.forKey(ClassLoader.getSystemClassLoader().getResourceAsStream("gpg.private"), "123456".toCharArray())
            .decrypt(new ByteArrayInputStream(encryptedBodyBytes.toByteArray()))
            .readAllBytes();

        assertThat(new String(decryptedPayload, StandardCharsets.UTF_8))
            .isEqualTo("""
                MIME-Version: 1.0\r
                Subject: small message\r
                Content-Type: multipart/report;\r
                 boundary="-=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=-"\r
                \r
                ---=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=-\r
                Content-Type: text/plain; charset=UTF-8\r
                Content-Transfer-Encoding: quoted-printable\r
                \r
                first\r
                ---=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=-\r
                Content-Type: message/disposition-notification\r
                \r
                Final-Recipient: rfc822; final_recipient\r
                ---=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=---\r
                """);
    }

    private static PGPKeyPair ecKeyPair(int algorithm, Instant creationTime) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new ECGenParameterSpec("P-256"));
        return new JcaPGPKeyPair(algorithm, generator.generateKeyPair(), Date.from(creationTime));
    }

    private static PGPSignatureSubpacketVector flags(int keyFlags, long expirationSeconds) {
        PGPSignatureSubpacketGenerator generator = new PGPSignatureSubpacketGenerator();
        generator.setKeyFlags(false, keyFlags);
        if (expirationSeconds != NO_EXPIRATION) {
            generator.setKeyExpirationTime(false, expirationSeconds);
        }
        return generator.generate();
    }

    private record Subkey(PGPKeyPair keyPair, PGPSignatureSubpacketVector flags) {
    }

    private static byte[] keyRing(PGPKeyPair primaryKey, PGPSignatureSubpacketVector primaryKeyFlags, Subkey... subkeys) throws Exception {
        PGPKeyRingGenerator generator = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, primaryKey,
            "bob <bob@domain.tld>", new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1),
            primaryKeyFlags, null,
            new JcaPGPContentSignerBuilder(primaryKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256), null);
        for (Subkey subkey : subkeys) {
            generator.addSubKey(subkey.keyPair(), subkey.flags(), null);
        }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        generator.generatePublicKeyRing().encode(encoded);
        return encoded.toByteArray();
    }
}
