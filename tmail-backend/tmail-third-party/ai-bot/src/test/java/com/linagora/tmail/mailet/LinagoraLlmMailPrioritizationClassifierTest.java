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
 *******************************************************************/

package com.linagora.tmail.mailet;

import static com.linagora.tmail.mailet.AIBotConfig.DEFAULT_TIMEOUT;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;

@Disabled("Manual run. Requires a valid Linagora AI's API key to be run")
public class LinagoraLlmMailPrioritizationClassifierTest implements LlmMailPrioritizationClassifierContract {
    private LlmMailPrioritizationClassifier testee;
    private UsersRepository usersRepository;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(Domain.of("example.com"));
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "password");
        usersRepository.addUser(ALICE, "password");

        AIBotConfig aiBotConfig = new AIBotConfig(
            Optional.ofNullable(System.getenv("LLM_API_KEY")).orElse("change-me"),
            new LlmModel("gpt-oss-120b"),
            Optional.of(URI.create("https://ai.linagora.com/api/v1/").toURL()),
            DEFAULT_TIMEOUT);
        StreamChatLanguageModelFactory streamChatLanguageModelFactory = new StreamChatLanguageModelFactory();
        StreamingChatLanguageModel chatLanguageModel = streamChatLanguageModelFactory.createChatLanguageModel(aiBotConfig);
        testee = new LlmMailPrioritizationClassifier(usersRepository, chatLanguageModel, new JsoupHtmlTextExtractor(),
            new RecordingMetricFactory());
    }

    @Override
    public LlmMailPrioritizationClassifier testee() {
        return testee;
    }
}
