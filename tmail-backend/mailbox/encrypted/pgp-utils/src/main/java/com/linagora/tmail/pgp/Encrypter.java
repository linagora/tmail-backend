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

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Date;

import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.stream.NameValuePair;
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
import com.google.common.io.FileBackedOutputStream;

public class Encrypter {
    private static final int FILE_THRESHOLD = 100 * 1024;

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

    public Message encrypt(Message clearMessage) throws Exception {
        DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
        BasicBodyFactory basicBodyFactory = new BasicBodyFactory();
        DefaultMessageWriter defaultMessageWriter = new DefaultMessageWriter();
        FileBackedOutputStream clearOutputStream = new FileBackedOutputStream(FILE_THRESHOLD);
        FileBackedOutputStream encryptedOutputStream = new FileBackedOutputStream(FILE_THRESHOLD);
        BufferedOutputStream bufferedEncryptedOutputStream = new BufferedOutputStream(encryptedOutputStream);
        BufferedOutputStream bufferedClearOutputStream = new BufferedOutputStream(clearOutputStream);

        try {
            defaultMessageWriter.writeMessage(clearMessage, bufferedClearOutputStream);
            bufferedClearOutputStream.close();
            encrypt(clearOutputStream.asByteSource(), bufferedEncryptedOutputStream);
            bufferedEncryptedOutputStream.close();

            Message encryptedMessage = messageBuilder.newMessage();
            Header header = messageBuilder.newHeader(clearMessage.getHeader());
            header.removeFields("Content-Type");
            encryptedMessage.setHeader(header);

            Multipart multipart = messageBuilder.newMultipart("encrypted", new NameValuePair("protocol", "application/pgp-encrypted"));
            multipart.addBodyPart(new BodyPartBuilder()
                .setBody(basicBodyFactory.binaryBody("Version: 1".getBytes(StandardCharsets.UTF_8)))
                .setContentType("application/pgp-encrypted")
                .build());
            multipart.addBodyPart(new BodyPartBuilder()
                .setContentType("application/octet-stream")
                .setBody(basicBodyFactory.binaryBody(encryptedOutputStream.asByteSource().openStream()))
                .build());
            encryptedMessage.setBody(multipart);


            final Message.Builder of = Message.Builder.of();
            header.getFields().forEach(of::addField);
            of.setBody(multipart);
            return of.build();
        } finally {
            clearOutputStream.reset();
            encryptedOutputStream.reset();
        }
    }
}
