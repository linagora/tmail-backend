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

package com.linagora.tmail.mailet;

import java.io.IOException;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import dev.ai4j.openai4j.OpenAiHttpException;
import reactor.core.publisher.Mono;


public class AIRedactionalHelperForTest implements AIRedactionalHelper {

    public Mono<String> suggestContent(String userInput, Optional<String> mailContent) throws OpenAiHttpException, IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(userInput), "User input cannot be null or empty");

        return Mono.just("This suggestion is just for testing purpose this is your UserInput: " + userInput + " This is you mailContent: " + mailContent.orElse(""));
    }

}
