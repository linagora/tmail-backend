/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.common.probe;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.james.jmap.label.LabelChangeRepository;
import com.linagora.tmail.james.jmap.label.LabelRepository;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelChange;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class JmapGuiceLabelProbe implements GuiceProbe {
    private final LabelRepository labelRepository;
    private final LabelChangeRepository labelChangeRepository;

    @Inject
    public JmapGuiceLabelProbe(LabelRepository labelRepository,
                               LabelChangeRepository labelChangeRepository) {
        this.labelRepository = labelRepository;
        this.labelChangeRepository = labelChangeRepository;
    }

    public Label addLabel(Username username, LabelCreationRequest labelCreationRequest) {
        return Mono.from(labelRepository.addLabel(username, labelCreationRequest))
            .block();
    }

    public List<Label> listLabels(Username username) {
        return Flux.from(labelRepository.listLabels(username))
            .collectList()
            .block();
    }

    public void saveLabelChange(LabelChange labelChange) {
        Mono.from(labelChangeRepository.save(labelChange)).block();
    }
}
