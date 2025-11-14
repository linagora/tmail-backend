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
package com.linagora.tmail.mailet.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LatestEmailReplyExtractorTest {

    private LatestEmailReplyExtractor latestEmailReplyExtractor;

    @BeforeEach
    void setUp() {
        latestEmailReplyExtractor = new LatestEmailReplyExtractor.RegexBased();
    }

    @Test
    void shouldReturnEmptyStringForNullContent() {
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(null)).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringForEmptyContent() {
        assertThat(latestEmailReplyExtractor.cleanQuotedContent("")).isEmpty();
    }

    @Test
    void shouldReturnOriginalContentWhenNoQuotes() {
        String content = """
                Hello John,

                Could you please send me the updated version of the report by Friday?

                Best regards,
                Alice""";

        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo(content);
    }

    @Test
    void shouldRemoveQuotedLinesStartingWithGreaterThan() {
        String content = """
                Thanks for the update!

                > On Tue, Oct 10, 2023 at 9:30 AM John Doe <john@example.com> wrote:
                > Hi Alice,
                > Please find attached the first version of the report.""";
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Thanks for the update!");
    }

    @Test
    void shouldHandleGmailReplyFormat() {
        String content = """
                Sure, I’ll handle this today.

                On Tue, Oct 10, 2023 at 2:15 PM Jane Smith <jane@example.com> wrote:
                The meeting has been rescheduled to 3 PM.""";
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Sure, I’ll handle this today.");
    }

    @Test
    void shouldHandleAppleMailReplyFormat() {
        String content = """
                Sure, I’ll handle this today.

                On Tue, Oct 10, 2023 at 2:15 PM Jane Smith <jane@example.com> wrote:
                The meeting has been rescheduled to 3 PM.
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Sure, I’ll handle this today.");
    }

    @Test
    void shouldHandleAppleMailFrenchFormat() {
        String content = """
                Oui, c’est parfait pour moi.

                Le mar. 10 oct. 2023 à 15:42, Jean Dupont <jean.dupont@example.com> a écrit :
                Bonjour Marie,
                Peux-tu valider le document joint ?
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Oui, c’est parfait pour moi.");
    }

    @Test
    void shouldHandleOutlookReplyFormat() {
        String content = """
                Here is my final answer.

                From: support@company.com
                Sent: Monday, October 10, 2023 10:00 AM
                To: user@example.com
                Subject: Request for confirmation
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Here is my final answer.");
    }

    @Test
    void shouldRemoveContentAfterSentPatternOfOutlookReplyFormat() {
        String content = """
                Let’s confirm this in the meeting.

                Sent: Monday, October 10, 2023 2:45 PM
                To: team@example.com
                Subject: Weekly report
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Let’s confirm this in the meeting.");
    }

    @Test
    void shouldRemoveContentAfterToPatternOfOutlookReplyFormat() {
        String content = """
                Please find the document attached.

                To: hr@company.com
                Subject: Contract Renewal
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Please find the document attached.");
    }

    @Test
    void shouldRemoveContentAfterSubjectPatternOfOutlookReplyFormat() {
        String content = """
                Thank you for your quick response.

                Subject: Re: Project Alpha - Final Delivery
                > Original message
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Thank you for your quick response.");
    }

    @Test
    void shouldHandleMultiplePatterns() {
        String content =  """
                Great, that works for me!

                > Previous discussion
                On 2023-10-10 John wrote:
                Thank you for the clarification.
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Great, that works for me!");
    }

    @Test
    void shouldHandleOutlookFrenchFormat() {
        String content = """
                Merci pour votre retour.

                De : support@company.com
                Envoyé : lundi 10 octobre 2023 10:00
                À : utilisateur@example.com
                Objet : Demande de confirmation
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Merci pour votre retour.");
    }

    @Test
    void shouldTrimWhitespace() {
        String content ="""
                  
                  Thanks for your help!   
                  
                """;
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo("Thanks for your help!");
    }

    @Test
    void shouldHandleTmailReplyFormat() {
        String content = """
            Hi Ale,
            
            Excellent, thank you for the quick follow-up and the data file. I've downloaded it and will begin the analysis shortly.
            
            Sounds good. I aim to have a first draft to you by Wednesday afternoon for your initial thoughts.
            
            Talk soon,
            Sam
            
            
            On Oct 28, 2025 3:35 pm, from Alae Mghirbi <amghirbi@linagora.com>, <amghirbi@linagora.com>
            Hi Sam,
            
            Glad to hear you’re doing well!
            
            Thanks a lot for confirming — I really appreciate it. I’ve just shared the latest data file with you via email. Let me know if you need any additional details or charts.
            
            Looking forward to seeing your part once it’s ready.
            
            Best,
            Sam""";
        String expected = """
            Hi Ale,
            
            Excellent, thank you for the quick follow-up and the data file. I've downloaded it and will begin the analysis shortly.
            
            Sounds good. I aim to have a first draft to you by Wednesday afternoon for your initial thoughts.
            
            Talk soon,
            Sam
            """.trim();
        assertThat(latestEmailReplyExtractor.cleanQuotedContent(content)).isEqualTo(expected);
    }

}