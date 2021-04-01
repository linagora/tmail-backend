package com.linagora.openpaas.gpg;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;

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
}