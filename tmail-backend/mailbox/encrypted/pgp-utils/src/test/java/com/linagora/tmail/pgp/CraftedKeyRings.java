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

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.List;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGKey;
import org.bouncycastle.bcpg.ElGamalPublicBCPGKey;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.PublicSubkeyPacket;
import org.bouncycastle.bcpg.RSAPublicBCPGKey;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;

/**
 * Armored key rings whose encryption subkey holds arbitrary, possibly unusable, key material.
 *
 * Such material has no private counterpart: the subkey binding signature, flagging it for encryption, is made by hand
 * with a genuine primary key.
 */
public class CraftedKeyRings {
    private static final BouncyCastleProvider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();
    private static final int ENCRYPT_FLAGS = KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE;

    public static byte[] withRSAEncryptionSubkey(BigInteger modulus, BigInteger exponent) throws Exception {
        return withEncryptionSubkey(PublicKeyAlgorithmTags.RSA_GENERAL, new RSAPublicBCPGKey(modulus, exponent));
    }

    public static byte[] withElGamalEncryptionSubkey(BigInteger prime) throws Exception {
        return withEncryptionSubkey(PublicKeyAlgorithmTags.ELGAMAL_GENERAL,
            new ElGamalPublicBCPGKey(prime, BigInteger.valueOf(5), BigInteger.valueOf(8)));
    }

    private static byte[] withEncryptionSubkey(int algorithm, BCPGKey subkeyMaterial) throws Exception {
        Date now = new Date();
        PGPKeyPair primaryKey = ecdsaKeyPair(now);
        PGPPublicKey subkey = new PGPPublicKey(new PublicSubkeyPacket(algorithm, now, subkeyMaterial), new BcKeyFingerprintCalculator());
        PGPPublicKeyRing keyRing = new PGPPublicKeyRing(List.of(primaryKey.getPublicKey(),
            PGPPublicKey.addCertification(subkey, encryptionBinding(primaryKey, subkey))));

        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (ArmoredOutputStream armored = new ArmoredOutputStream(encoded)) {
            keyRing.encode(armored);
        }
        return encoded.toByteArray();
    }

    private static PGPKeyPair ecdsaKeyPair(Date creationTime) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BOUNCY_CASTLE_PROVIDER);
        generator.initialize(new ECGenParameterSpec("P-256"));
        return new JcaPGPKeyPair(PublicKeyAlgorithmTags.ECDSA, generator.generateKeyPair(), creationTime);
    }

    private static PGPSignature encryptionBinding(PGPKeyPair primaryKey, PGPPublicKey subkey) throws Exception {
        PGPSignatureGenerator generator = new PGPSignatureGenerator(
            new JcaPGPContentSignerBuilder(primaryKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256).setProvider(BOUNCY_CASTLE_PROVIDER),
            primaryKey.getPublicKey());
        generator.init(PGPSignature.SUBKEY_BINDING, primaryKey.getPrivateKey());
        PGPSignatureSubpacketGenerator hashedSubpackets = new PGPSignatureSubpacketGenerator();
        hashedSubpackets.setKeyFlags(false, ENCRYPT_FLAGS);
        generator.setHashedSubpackets(hashedSubpackets.generate());
        return generator.generateCertification(primaryKey.getPublicKey(), subkey);
    }
}
