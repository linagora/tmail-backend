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

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.util.html.HtmlTextExtractor;

import com.linagora.tmail.common.chatlanguagemodel.LlmConfig;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

/**
 * Backward-compatible wrapper for the renamed AIBotMailet.
 *
 * @deprecated Use {@link com.linagora.tmail.aibotreplier.AIBotMailet} instead.
 * This class is kept only for backward compatibility with existing deployments
 * that reference the old fully qualified class name.
 */
@Deprecated
public class AIBotMailet extends com.linagora.tmail.aibotreplier.AIBotMailet {

    @Inject
    public AIBotMailet(LlmConfig llmConfig, StreamingChatLanguageModel chatLanguageModel, HtmlTextExtractor htmlTextExtractor) {
        super(llmConfig, chatLanguageModel, htmlTextExtractor);
    }

    public AIBotMailet(LlmConfig llmConfig, MailAddress botAddress, StreamingChatLanguageModel chatLanguageModel, HtmlTextExtractor htmlTextExtractor) {
        super(llmConfig, botAddress, chatLanguageModel, htmlTextExtractor);
    }
}
