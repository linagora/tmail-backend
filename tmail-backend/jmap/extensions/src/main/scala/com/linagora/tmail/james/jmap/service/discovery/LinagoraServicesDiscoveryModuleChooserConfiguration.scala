package com.linagora.tmail.james.jmap.service.discovery

import java.io.FileNotFoundException

import org.apache.james.utils.PropertiesProvider
import org.slf4j.{Logger, LoggerFactory}

case class LinagoraServicesDiscoveryModuleChooserConfiguration(enable: Boolean)

object LinagoraServicesDiscoveryModuleChooserConfiguration {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[LinagoraServicesDiscoveryModuleChooserConfiguration])

  def parse(propertiesProvider: PropertiesProvider): LinagoraServicesDiscoveryModuleChooserConfiguration =
    try {
      propertiesProvider.getConfiguration("linagora-ecosystem")
      LOGGER.info("Turned on Linagora services discovery module.")
      new LinagoraServicesDiscoveryModuleChooserConfiguration(true)
    } catch {
      case _: FileNotFoundException =>
        LOGGER.info("Turned off Linagora services discovery module.")
        new LinagoraServicesDiscoveryModuleChooserConfiguration(false)
    }
}
