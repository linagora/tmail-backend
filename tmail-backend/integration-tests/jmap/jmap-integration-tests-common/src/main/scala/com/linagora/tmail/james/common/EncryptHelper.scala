package com.linagora.tmail.james.common

import com.google.common.io.ByteSource
import com.linagora.tmail.pgp.{Decrypter, Encrypter}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

object EncryptHelper {
  lazy val PGP_KEY: Array[Byte] = ClassLoader.getSystemClassLoader
    .getResourceAsStream("gpg.pub")
    .readAllBytes()

  lazy val PGP_KEY_ARMORED: String = new String(PGP_KEY, StandardCharsets.UTF_8)
    .replace("\n", "\\n")

  lazy val ENCRYPTER: Encrypter = Encrypter.forKeys(PGP_KEY)
  lazy val DECRYPTER: Decrypter = Decrypter.forKey(ClassLoader.getSystemClassLoader
    .getResourceAsStream("gpg.private"),
    "123456".toCharArray)

  def encrypt(byteSource: ByteSource): String = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream
    ENCRYPTER.encrypt(byteSource, stream)
    new String(stream.toByteArray, StandardCharsets.UTF_8)
  }

  def decrypt(encryptedPayload: String): String = {
    val decryptedPayload: Array[Byte] = DECRYPTER.decrypt(new ByteArrayInputStream(encryptedPayload.getBytes))
      .readAllBytes()
    new String(decryptedPayload, StandardCharsets.UTF_8)
  }
}
