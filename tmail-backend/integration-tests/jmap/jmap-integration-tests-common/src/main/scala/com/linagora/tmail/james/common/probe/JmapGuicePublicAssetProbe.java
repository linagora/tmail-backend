package com.linagora.tmail.james.common.probe;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.james.jmap.publicAsset.PublicAssetCreationRequest;
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetRepository;
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetStorage;

import reactor.core.publisher.Mono;

public class JmapGuicePublicAssetProbe implements GuiceProbe {
    private final PublicAssetRepository publicAssetRepository;

    @Inject
    public JmapGuicePublicAssetProbe(PublicAssetRepository publicAssetRepository) {
        this.publicAssetRepository = publicAssetRepository;
    }

    public PublicAssetStorage addPublicAsset(Username username, PublicAssetCreationRequest creationRequest) {
        return Mono.from(publicAssetRepository.create(username, creationRequest))
            .block();
    }
}
