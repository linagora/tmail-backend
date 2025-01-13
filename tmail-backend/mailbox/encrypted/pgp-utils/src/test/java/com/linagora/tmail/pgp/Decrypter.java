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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class Decrypter {
    public static Decrypter forKey(InputStream keyIn, char[] pass) throws Exception {
        return new Decrypter(retrievePrivateKey(keyIn, pass));
    }

    private static PGPPrivateKey retrievePrivateKey(InputStream keyIn, char[] pass) throws Exception {
        return asSecretKey(keyIn)
            .extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(pass));
    }

    private static PGPSecretKey asSecretKey(InputStream in) throws Exception {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(in), new JcaKeyFingerprintCalculator());

        return ImmutableList.copyOf(pgpSec.getKeyRings())
            .stream()
            .map(PGPSecretKeyRing::getSecretKey)
            .filter(PGPSecretKey::isSigningKey)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Can't find signing key in key ring."));
    }

    private final PGPPrivateKey privateKey;

    private Decrypter(PGPPrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public InputStream decrypt(InputStream in) throws Exception {
        PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(PGPUtil.getDecoderStream(in), new BcKeyFingerprintCalculator());
        Object nextObject = pgpObjectFactory.nextObject();
        PGPEncryptedDataList pgpEncryptedData = retrievePgpEncryptedData(pgpObjectFactory, nextObject);

        return ImmutableList.copyOf(pgpEncryptedData.getEncryptedDataObjects())
            .stream()
            .filter(PGPPublicKeyEncryptedData.class::isInstance)
            .map(PGPPublicKeyEncryptedData.class::cast)
            .flatMap(Throwing.<PGPPublicKeyEncryptedData, Stream<InputStream>>function(this::decrypt).orReturn(Stream.empty()))
            .findFirst()
            .orElseThrow(() -> new PGPException("Decryption failed"));
    }

    private PGPEncryptedDataList retrievePgpEncryptedData(PGPObjectFactory pgpF, Object o) throws IOException {
        if (o instanceof PGPEncryptedDataList pgpEncryptedDataList) {
            return pgpEncryptedDataList;
        }
        return (PGPEncryptedDataList) pgpF.nextObject();
    }

    private Stream<InputStream> decrypt(PGPPublicKeyEncryptedData pgpPublicKeyEncryptedData) throws PGPException, IOException {
        InputStream clear = pgpPublicKeyEncryptedData.getDataStream(new BcPublicKeyDataDecryptorFactory(privateKey));
        PGPObjectFactory plainFact = new PGPObjectFactory(clear, new BcKeyFingerprintCalculator());
        Object message = retrieveMessage(plainFact);

        if (message instanceof PGPLiteralData literalData) {
            byte[] bytes = literalData.getInputStream().readAllBytes();
            assertIntegrity(pgpPublicKeyEncryptedData);
            return Stream.of(new ByteArrayInputStream(bytes));
        }
        throw new PGPException("message is not a simple encrypted file - type unknown.");
    }

    private void assertIntegrity(PGPPublicKeyEncryptedData pgpPublicKeyEncryptedData) throws PGPException, IOException {
        if (pgpPublicKeyEncryptedData.isIntegrityProtected()
                && !pgpPublicKeyEncryptedData.verify()) {
            throw new PGPException("message failed integrity check");
        }
    }

    private Object retrieveMessage(PGPObjectFactory plainFact) throws IOException, PGPException {
        Object message = plainFact.nextObject();
        if (message instanceof PGPCompressedData compressedData) {
            PGPObjectFactory pgpObjectFactory = new PGPObjectFactory(compressedData.getDataStream(), new BcKeyFingerprintCalculator());
            return byPassOnePassSignatureList(pgpObjectFactory.nextObject(), pgpObjectFactory);
        }
        throw new PGPException("Expecting compressed data");
    }

    private Object byPassOnePassSignatureList(Object message, PGPObjectFactory pgpFact) throws IOException {
        if (message instanceof PGPOnePassSignatureList) {
            return pgpFact.nextObject();
        }
        return message;
    }
}
