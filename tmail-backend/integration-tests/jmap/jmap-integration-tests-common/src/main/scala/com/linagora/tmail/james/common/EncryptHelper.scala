package com.linagora.tmail.james.common

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

import com.google.common.io.ByteSource
import com.linagora.tmail.pgp.{Decrypter, Encrypter}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.`given`
import io.restassured.specification.RequestSpecification
import org.apache.http.HttpStatus
import org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER

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

  def uploadPublicKey(accountId: String, requestSpecification: RequestSpecification): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["Keystore/set", {
         |      "accountId": "$accountId",
         |      "create": {
         |        "K87": {
         |          "key": "$PGP_KEY_ARMORED"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    `given`(requestSpecification)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
  }
}
