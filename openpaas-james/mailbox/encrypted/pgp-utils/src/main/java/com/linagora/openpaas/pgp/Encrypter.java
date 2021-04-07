package com.linagora.openpaas.pgp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Date;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

public class Encrypter {
    public static Encrypter forKeys(Collection<byte[]> armoredKeys) {
        return new Encrypter(createEncryptor(armoredKeys));
    }

    public static Encrypter forKeys(byte[]... armoredKeys) {
        return forKeys(ImmutableList.copyOf(armoredKeys));
    }

    private static PGPEncryptedDataGenerator createEncryptor(Collection<byte[]> armoredKeys) {
        PGPEncryptedDataGenerator pgpEncryptedDataGenerator = new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_128)
            .setSecureRandom(new SecureRandom())
            .setProvider("BC")
            .setWithIntegrityPacket(true));

        armoredKeys.stream()
            .map(ByteArrayInputStream::new)
            .map(Throwing.function(Encrypter::readPublicKey))
            .map(JcePublicKeyKeyEncryptionMethodGenerator::new)
            .forEach(pgpEncryptedDataGenerator::addMethod);
        return pgpEncryptedDataGenerator;
    }

    public static PGPPublicKey readPublicKey(InputStream in) throws Exception {
        PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(in),
            new BcKeyFingerprintCalculator());

        return ImmutableList.copyOf(pgpPub.getKeyRings())
            .stream()
            .map(PGPPublicKeyRing::getPublicKey)
            .filter(PGPPublicKey::isEncryptionKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Can't find encryption key in key ring."));
    }

    private final PGPEncryptedDataGenerator pgpEncryptedDataGenerator;

    private Encrypter(PGPEncryptedDataGenerator pgpEncryptedDataGenerator) {
        this.pgpEncryptedDataGenerator = pgpEncryptedDataGenerator;
    }

    public void encrypt(ByteSource byteSource, OutputStream output) throws Exception {
        PGPCompressedDataGenerator compressor = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
        PGPLiteralDataGenerator literalDataGenerator = new PGPLiteralDataGenerator();

        try (ArmoredOutputStream armoredOutputStream = new ArmoredOutputStream(output);
            OutputStream encryptedOutStream = pgpEncryptedDataGenerator.open(armoredOutputStream, new byte[100 * 1024]);
            OutputStream compressedOutStream = compressor.open(encryptedOutStream);
            OutputStream literalDataOutStream = literalDataGenerator.open(compressedOutStream, PGPLiteralData.BINARY,
                "encrypted.pgp", byteSource.size(), new Date())) {
            byteSource.openBufferedStream().transferTo(literalDataOutStream);
        }
    }
}
