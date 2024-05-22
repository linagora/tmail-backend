package com.linagora.tmail.james.jmap.publicAsset

import java.net.URI
import java.util.UUID

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.AccountId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PublicAssetURITest {

  @Test
  def fromShouldReturnCorrectPublicURI(): Unit = {
    val prefixUri = new URI("http://localhost:8080")
    val assetId = PublicAssetId(UUID.fromString("a0f7b4b3-0b1b-4b3b-8b3b-0b1b4b3b8b3b"))
    val account = AccountId("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8")
    val result: PublicURI = PublicURI.from(assetId, account, prefixUri)

    assertThat(result.value.toString).isEqualTo("http://localhost:8080/publicAsset/aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8/a0f7b4b3-0b1b-4b3b-8b3b-0b1b4b3b8b3b")
  }

  @Test
  def fromShouldReturnCorrectPublicURIWhenPrefixUriContainSeveralPathSegments(): Unit = {
    val prefixUri = new URI("http://localhost:8080/jmap/abc")
    val assetId = PublicAssetId(UUID.fromString("a0f7b4b3-0b1b-4b3b-8b3b-0b1b4b3b8b3b"))
    val account = AccountId("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8")
    val result: PublicURI = PublicURI.from(assetId, account, prefixUri)

    assertThat(result.value.toString).isEqualTo("http://localhost:8080/jmap/abc/publicAsset/aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8/a0f7b4b3-0b1b-4b3b-8b3b-0b1b4b3b8b3b")
  }
}
