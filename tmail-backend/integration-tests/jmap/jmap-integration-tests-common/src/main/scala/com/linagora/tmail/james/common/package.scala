package com.linagora.tmail.james

import java.time.Duration
import java.util.concurrent.TimeUnit

import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory

package object common {
  private lazy val slowPacedPollInterval: Duration = ONE_HUNDRED_MILLISECONDS
  lazy val calmlyAwait: ConditionFactory = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await

  lazy val awaitAtMostTenSeconds: ConditionFactory = calmlyAwait.atMost(10, TimeUnit.SECONDS)
}
