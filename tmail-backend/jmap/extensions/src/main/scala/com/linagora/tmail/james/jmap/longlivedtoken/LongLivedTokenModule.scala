package com.linagora.tmail.james.jmap.longlivedtoken

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import org.apache.james.jmap.http.AuthenticationStrategy

class LongLivedTokenModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[InMemoryLongLivedTokenStore]).in(Scopes.SINGLETON)
    bind(classOf[LongLivedTokenStore]).to(classOf[InMemoryLongLivedTokenStore])

    Multibinder.newSetBinder(binder, classOf[AuthenticationStrategy])
      .addBinding().to(classOf[LongLivedTokenAuthenticationStrategy])
  }
}


