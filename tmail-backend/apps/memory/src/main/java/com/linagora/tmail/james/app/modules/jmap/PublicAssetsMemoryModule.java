package com.linagora.tmail.james.app.modules.jmap;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.linagora.tmail.james.jmap.publicAsset.MemoryPublicAssetRepository;
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetRepository;

public class PublicAssetsMemoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MemoryPublicAssetRepository.class).in(Scopes.SINGLETON);
        bind(PublicAssetRepository.class).to(MemoryPublicAssetRepository.class);
    }
}
