package com.linagora.tmail.james.common.probe;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.james.jmap.label.LabelRepository;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;

import reactor.core.publisher.Mono;

public class JmapGuiceLabelProbe implements GuiceProbe {
    private final LabelRepository labelRepository;

    @Inject
    public JmapGuiceLabelProbe(LabelRepository labelRepository) {
        this.labelRepository = labelRepository;
    }

    public Label addLabel(Username username, LabelCreationRequest labelCreationRequest) {
        return Mono.from(labelRepository.addLabel(username, labelCreationRequest))
            .block();
    }
}
