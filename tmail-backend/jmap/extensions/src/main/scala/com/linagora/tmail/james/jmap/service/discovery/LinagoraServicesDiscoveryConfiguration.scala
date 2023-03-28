package com.linagora.tmail.james.jmap.service.discovery;

import java.net.URL

import org.apache.commons.configuration2.Configuration;

case class LinagoraServicesDiscoveryConfiguration(linShareApiUrl: URL,
                                                  linToApiUrl: URL,
                                                  linToApiKey: String,
                                                  twakeApiUrl: URL)

object LinagoraServicesDiscoveryConfiguration {
  def from(configuration: Configuration): LinagoraServicesDiscoveryConfiguration = {
    val linShareApiUrl: URL = Option(configuration.getString("linshareApiUrl", null))
      .filter(_.nonEmpty)
      .map(string => new URL(string))
      .getOrElse(throw new IllegalArgumentException("Missing LinShare URL configuration"))

    val linToApiUrl: URL = Option(configuration.getString("linToApiUrl", null))
      .filter(_.nonEmpty)
      .map(string => new URL(string))
      .getOrElse(throw new IllegalArgumentException("Missing LinTo URL configuration"))

    val linToApiKey: String = Option(configuration.getString("linToApiKey", null))
      .filter(_.nonEmpty)
      .getOrElse(throw new IllegalArgumentException("Missing LinTo API Key configuration"))

    val twakeApiUrl: URL = Option(configuration.getString("twakeApiUrl", null))
      .filter(_.nonEmpty)
      .map(string => new URL(string))
      .getOrElse(throw new IllegalArgumentException("Missing Twake URL configuration"))

    LinagoraServicesDiscoveryConfiguration(linShareApiUrl, linToApiUrl, linToApiKey, twakeApiUrl)
  }
}