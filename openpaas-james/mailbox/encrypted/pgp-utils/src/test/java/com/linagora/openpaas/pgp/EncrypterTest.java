package com.linagora.openpaas.pgp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;

import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.field.Fields;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.message.SingleBodyBuilder;
import org.apache.james.mime4j.stream.NameValuePair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.common.io.ByteSource;

class EncrypterTest {
    public static final String INPUT = "ga bou zo meuh\r\nBienvenue chez les shadocks.\r\n";

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

        String encryptedMessageAsString = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertThat(encryptedMessageAsString)
            .startsWith("MIME-Version: 1.0\r\n" +
                "Subject: small message\r\n" +
                "Content-Type: multipart/encrypted; protocol=\"application/pgp-encrypted\";\r\n" +
                " boundary=\"");
        assertThat(encryptedMessageAsString).contains("Content-Type: application/pgp-encrypted\r\n" +
            "\r\n" +
            "Version: 1\r\n");
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
            .isEqualTo("MIME-Version: 1.0\r\n" +
                "Subject: small message\r\n" +
                "Content-Type: multipart/report;\r\n" +
                " boundary=\"-=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=-\"\r\n" +
                "\r\n" +
                "---=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=-\r\n" +
                "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "\r\n" +
                "first\r\n" +
                "---=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=-\r\n" +
                "Content-Type: message/disposition-notification\r\n" +
                "\r\n" +
                "Final-Recipient: rfc822; final_recipient\r\n" +
                "---=Part.0.9726a619aa3f23a9.178aa7b7a6b.e3060dccb56a8d65=---\r\n");
    }
}