package com.linagora.tmail.james.common

import org.apache.james.jmap.rfc8621.contract.Fixture.{ALICE, ALICE_PASSWORD, BOB, BOB_PASSWORD}
import org.junit.jupiter.api.extension.{ExtensionContext, ParameterContext, ParameterResolver}

class DefaultEventInvitationParameterResolver extends ParameterResolver {

  override def supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.getParameter.getType eq classOf[EventInvitation]

  override def resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): AnyRef =
    EventInvitation(
      sender = User("ALICE", ALICE.asString(), ALICE_PASSWORD),
      receiver = User("BOB", BOB.asString(), BOB_PASSWORD))
}
