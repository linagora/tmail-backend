package com.linagora.tmail.james.app;

import org.apache.james.jmap.routes.BlobResolver;
import org.apache.james.jmap.routes.MessageBlobResolver;
import org.apache.james.jmap.routes.MessagePartBlobResolver;
import org.apache.james.jmap.routes.UploadResolver;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class TMailCleverBlobResolverModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<BlobResolver> blobResolverMultibinder = Multibinder.newSetBinder(binder(), BlobResolver.class);
        blobResolverMultibinder.addBinding().to(MessageBlobResolver.class);
        blobResolverMultibinder.addBinding().to(UploadResolver.class);
        blobResolverMultibinder.addBinding().to(TMailCleverBlobResolver.class);
    }
}
