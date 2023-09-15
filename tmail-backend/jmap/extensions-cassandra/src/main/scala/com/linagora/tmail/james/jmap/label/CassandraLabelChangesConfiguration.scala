package com.linagora.tmail.james.jmap.label

import java.time.Duration
import java.time.temporal.ChronoUnit

import com.google.common.base.Preconditions
import org.apache.commons.configuration2.Configuration
import org.apache.james.util.DurationParser

object CassandraLabelChangesConfiguration {
  private val DEFAULT_TTL: Duration = Duration.ofDays(60)
  val DEFAULT: CassandraLabelChangesConfiguration = CassandraLabelChangesConfiguration(DEFAULT_TTL)

  def from(configuration: Configuration): CassandraLabelChangesConfiguration = {
    val labelChangeTtl: Duration = Option(configuration.getString("label.change.ttl", null))
      .map(value => DurationParser.parse(value, ChronoUnit.SECONDS))
      .getOrElse(DEFAULT_TTL)

    Preconditions.checkArgument(labelChangeTtl.getSeconds > 0, "'TTL' needs to be positive".asInstanceOf[Object])
    Preconditions.checkArgument(labelChangeTtl.getSeconds < Integer.MAX_VALUE, s"'TTL' must not greater than ${Integer.MAX_VALUE} sec".asInstanceOf[Object])

    CassandraLabelChangesConfiguration(labelChangeTtl)
  }
}

case class CassandraLabelChangesConfiguration(labelChangeTtl: Duration)
