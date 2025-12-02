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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.apache.james.core.Domain;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;

import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;

public class MockLlmMailPrioritizationClassifierTest implements LlmMailPrioritizationClassifierContract {
    private LlmMailPrioritizationClassifier testee;
    private String mockLlmAnswer;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(Domain.of("example.com"));
        UsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "password");
        usersRepository.addUser(ALICE, "password");

        StreamingChatLanguageModel chatLanguageModel = mock(StreamingChatLanguageModel.class);
        doAnswer(invocation -> {
            StreamingResponseHandler<String> handler = invocation.getArgument(1);
            handler.onNext(mockLlmAnswer);
            handler.onComplete(null);
            return null;
        }).when(chatLanguageModel).generate(anyList(), any());

        testee = new LlmMailPrioritizationClassifier(usersRepository, chatLanguageModel, new JsoupHtmlTextExtractor(),
            new RecordingMetricFactory());
    }

    @Override
    public LlmMailPrioritizationClassifier testee() {
        return testee;
    }

    @Override
    public void needActionsLlmHook() {
        mockLlmAnswer = "YES";
    }

    @Override
    public void noNeedActionsLlmHook() {
        mockLlmAnswer = "NO";
    }
}
