package com.linagora.openpaas.james.jmap

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.method.{InvocationWithContext, Method}
import org.apache.james.mailbox.MailboxSession
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class CustomMethodModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CustomMethod])
  }
}

class CustomMethod extends Method {
  override val methodName = MethodName("Linagora/echo")

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = SMono.just(invocation)

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE)
}
