package com.linagora.tmail.james.jmap.service.discovery;

import org.apache.commons.configuration2.Configuration

import scala.jdk.CollectionConverters._

case class LinagoraServicesDiscoveryItem(key: String, value: String)

case class LinagoraServicesDiscoveryConfiguration(services: List[LinagoraServicesDiscoveryItem]) {
  def getServicesAsJava(): java.util.List[LinagoraServicesDiscoveryItem] = services.asJava
}

object LinagoraServicesDiscoveryConfiguration {
  def from(configuration: Configuration): LinagoraServicesDiscoveryConfiguration = {
    val services: List[LinagoraServicesDiscoveryItem] = configuration.getKeys
      .asScala
      .toList
      .map(key => LinagoraServicesDiscoveryItem(key, configuration.getString(key)))

    LinagoraServicesDiscoveryConfiguration(services)
  }
}