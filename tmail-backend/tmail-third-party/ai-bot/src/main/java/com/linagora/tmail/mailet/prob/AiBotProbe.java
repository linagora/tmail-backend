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
package com.linagora.tmail.mailet.prob;

import jakarta.inject.Inject;

import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.mailet.AIRedactionalHelper;



public class AiBotProbe implements GuiceProbe {

    private final AIRedactionalHelper aiRedactionalHelper;

    @Inject
    public AiBotProbe(AIRedactionalHelper aiRedactionalHelper) {
        this.aiRedactionalHelper = aiRedactionalHelper;
    }

    public AIRedactionalHelper getAiRedactionalHelper() {
        return aiRedactionalHelper;
    }
}
