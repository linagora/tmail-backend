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

package com.linagora.tmail.listener.rag.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MessageFilterParserTest {
    private SystemMailboxesProvider systemMailboxesProvider;
    private MessageFilterParser parser;

    @BeforeEach
    void setUp() {
        systemMailboxesProvider = mock(SystemMailboxesProvider.class);
        parser = new MessageFilterParser(systemMailboxesProvider);
    }

    @Nested
    class ParseNoFilterConfiguration {
        @Test
        void shouldReturnAllFilterWhenNoFilterConfigurationExists() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.systemPrompt", "test");

            MessageFilter result = parser.parse(config);

            assertThat(result).isEqualTo(MessageFilter.ALL);
        }

        @Test
        void shouldReturnAllFilterWhenConfigurationIsEmpty() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();

            MessageFilter result = parser.parse(config);

            assertThat(result).isEqualTo(MessageFilter.ALL);
        }
    }

    @Nested
    class ParseHeuristicFilters {
        @Test
        void shouldParseInboxFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.isInINBOX", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(InboxFilter.class);
        }

        @Test
        void shouldParseMainRecipientFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.isMainRecipient", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(MainRecipientFilter.class);
        }

        @Test
        void shouldParseAutoSubmittedFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.isAutoSubmitted", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(AutoSubmittedFilter.class);
        }

        @Test
        void shouldParseHasHeaderFilterWithNameOnly() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.hasHeader[@name]", "X-Custom-Header");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(HasHeaderFilter.class);
            HasHeaderFilter hasHeaderFilter = (HasHeaderFilter) result;
            assertThat(hasHeaderFilter.getHeaderName()).isEqualTo("X-Custom-Header");
            assertThat(hasHeaderFilter.getExpectedValue()).isEmpty();
        }

        @Test
        void shouldParseHasHeaderFilterWithNameAndValue() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.hasHeader[@name]", "X-Priority");
            config.addProperty("listener.configuration.filter.hasHeader[@value]", "1");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(HasHeaderFilter.class);
            HasHeaderFilter hasHeaderFilter = (HasHeaderFilter) result;
            assertThat(hasHeaderFilter.getHeaderName()).isEqualTo("X-Priority");
            assertThat(hasHeaderFilter.getExpectedValue()).contains("1");
        }

        @Test
        void shouldThrowExceptionWhenHasHeaderFilterMissingName() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.hasHeader[@value]", "test");

            assertThatThrownBy(() -> parser.parse(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hasHeader filter requires 'name' attribute");
        }
    }

    @Nested
    class ParseLogicalOperators {
        @Test
        void shouldParseAndFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.and.isInINBOX", "");
            config.addProperty("listener.configuration.filter.and.isMainRecipient", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(AndFilter.class);
            AndFilter andFilter = (AndFilter) result;
            assertThat(andFilter.getFilters()).hasSize(2);
            assertThat(andFilter.getFilters().get(0)).isInstanceOf(InboxFilter.class);
            assertThat(andFilter.getFilters().get(1)).isInstanceOf(MainRecipientFilter.class);
        }

        @Test
        void shouldParseOrFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.or.isInINBOX", "");
            config.addProperty("listener.configuration.filter.or.isAutoSubmitted", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(OrFilter.class);
            OrFilter orFilter = (OrFilter) result;
            assertThat(orFilter.getFilters()).hasSize(2);
            assertThat(orFilter.getFilters().get(0)).isInstanceOf(InboxFilter.class);
            assertThat(orFilter.getFilters().get(1)).isInstanceOf(AutoSubmittedFilter.class);
        }

        @Test
        void shouldParseNotFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.not.isAutoSubmitted", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(NotFilter.class);
            NotFilter notFilter = (NotFilter) result;
            assertThat(notFilter.getFilter()).isInstanceOf(AutoSubmittedFilter.class);
        }

        @Test
        void shouldThrowExceptionWhenAndFilterHasNoChildren() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.and", "");

            assertThatThrownBy(() -> parser.parse(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AndFilter requires at least one child filter");
        }

        @Test
        void shouldThrowExceptionWhenOrFilterHasNoChildren() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.or", "");

            assertThatThrownBy(() -> parser.parse(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrFilter requires at least one child filter");
        }

        @Test
        void shouldThrowExceptionWhenNotFilterHasNoChild() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.not", "");

            assertThatThrownBy(() -> parser.parse(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not filter must have exactly one child filter");
        }

        @Test
        void shouldThrowExceptionWhenNotFilterHasMultipleChildren() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.not.isInINBOX", "");
            config.addProperty("listener.configuration.filter.not.isMainRecipient", "");

            assertThatThrownBy(() -> parser.parse(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not filter must have exactly one child filter");
        }
    }

    @Nested
    class ParseNestedFilters {
        @Test
        void shouldParseNestedAndOrFilters() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.and.isInINBOX", "");
            config.addProperty("listener.configuration.filter.and.or.isMainRecipient", "");
            config.addProperty("listener.configuration.filter.and.or.hasHeader[@name]", "X-Priority");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(AndFilter.class);
            AndFilter andFilter = (AndFilter) result;
            assertThat(andFilter.getFilters()).hasSize(2);
            assertThat(andFilter.getFilters().get(0)).isInstanceOf(InboxFilter.class);
            assertThat(andFilter.getFilters().get(1)).isInstanceOf(OrFilter.class);

            OrFilter orFilter = (OrFilter) andFilter.getFilters().get(1);
            assertThat(orFilter.getFilters()).hasSize(2);
            assertThat(orFilter.getFilters().get(0)).isInstanceOf(MainRecipientFilter.class);
            assertThat(orFilter.getFilters().get(1)).isInstanceOf(HasHeaderFilter.class);
        }

        @Test
        void shouldParseAndWithNotFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.and.isInINBOX", "");
            config.addProperty("listener.configuration.filter.and.isMainRecipient", "");
            config.addProperty("listener.configuration.filter.and.not.isAutoSubmitted", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(AndFilter.class);
            AndFilter andFilter = (AndFilter) result;
            assertThat(andFilter.getFilters()).hasSize(3);
            assertThat(andFilter.getFilters().get(0)).isInstanceOf(InboxFilter.class);
            assertThat(andFilter.getFilters().get(1)).isInstanceOf(MainRecipientFilter.class);
            assertThat(andFilter.getFilters().get(2)).isInstanceOf(NotFilter.class);

            NotFilter notFilter = (NotFilter) andFilter.getFilters().get(2);
            assertThat(notFilter.getFilter()).isInstanceOf(AutoSubmittedFilter.class);
        }

        @Test
        void shouldParseDeeplyNestedFilters() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.or.and.isInINBOX", "");
            config.addProperty("listener.configuration.filter.or.and.not.isAutoSubmitted", "");
            config.addProperty("listener.configuration.filter.or.isMainRecipient", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(OrFilter.class);
            OrFilter orFilter = (OrFilter) result;
            assertThat(orFilter.getFilters()).hasSize(2);
            assertThat(orFilter.getFilters().get(0)).isInstanceOf(AndFilter.class);
            assertThat(orFilter.getFilters().get(1)).isInstanceOf(MainRecipientFilter.class);

            AndFilter andFilter = (AndFilter) orFilter.getFilters().get(0);
            assertThat(andFilter.getFilters()).hasSize(2);
            assertThat(andFilter.getFilters().get(0)).isInstanceOf(InboxFilter.class);
            assertThat(andFilter.getFilters().get(1)).isInstanceOf(NotFilter.class);
        }
    }

    @Nested
    class ParseInvalidConfiguration {
        @Test
        void shouldThrowExceptionForUnknownFilterType() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.unknownFilter", "");

            assertThatThrownBy(() -> parser.parse(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown filter type: unknownFilter");
        }

        @Test
        void shouldThrowExceptionWhenFilterConfigurationHasMultipleRootElements() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.isInINBOX", "");
            config.addProperty("listener.configuration.filter.isMainRecipient", "");

            assertThatThrownBy(() -> parser.parse(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filter configuration must have exactly one root element");
        }

        @Test
        void shouldThrowExceptionWhenFilterConfigurationIsEmpty() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter", "");

            assertThatThrownBy(() -> parser.parse(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filter configuration must have at least one child element");
        }
    }

    @Nested
    class RealWorldScenarios {
        @Test
        void shouldParseTypicalPrioritizationFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.and.isInINBOX", "");
            config.addProperty("listener.configuration.filter.and.isMainRecipient", "");
            config.addProperty("listener.configuration.filter.and.not.isAutoSubmitted", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(AndFilter.class);
            AndFilter andFilter = (AndFilter) result;
            assertThat(andFilter.getFilters()).hasSize(3)
                .satisfies(filters -> {
                    assertThat(filters.get(0)).isInstanceOf(InboxFilter.class);
                    assertThat(filters.get(1)).isInstanceOf(MainRecipientFilter.class);
                    assertThat(filters.get(2)).isInstanceOf(NotFilter.class);
                });
        }

        @Test
        void shouldParseFilterWithCustomHeaders() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.and.isInINBOX", "");
            config.addProperty("listener.configuration.filter.and.hasHeader[@name]", "X-Priority");
            config.addProperty("listener.configuration.filter.and.hasHeader[@value]", "1");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(AndFilter.class);
            AndFilter andFilter = (AndFilter) result;
            assertThat(andFilter.getFilters()).hasSize(2);
            assertThat(andFilter.getFilters().get(1)).isInstanceOf(HasHeaderFilter.class);

            HasHeaderFilter hasHeaderFilter = (HasHeaderFilter) andFilter.getFilters().get(1);
            assertThat(hasHeaderFilter.getHeaderName()).isEqualTo("X-Priority");
            assertThat(hasHeaderFilter.getExpectedValue()).contains("1");
        }

        @Test
        void shouldParseComplexBusinessLogicFilter() {
            HierarchicalConfiguration<ImmutableNode> config = new BaseHierarchicalConfiguration();
            config.addProperty("listener.configuration.filter.and.isInINBOX", "");
            config.addProperty("listener.configuration.filter.and.or.isMainRecipient", "");
            config.addProperty("listener.configuration.filter.and.or.hasHeader[@name]", "X-Important");
            config.addProperty("listener.configuration.filter.and.not.isAutoSubmitted", "");

            MessageFilter result = parser.parse(config);

            assertThat(result).isInstanceOf(AndFilter.class);
            AndFilter andFilter = (AndFilter) result;
            assertThat(andFilter.getFilters()).hasSize(3);
            assertThat(andFilter.getFilters().get(0)).isInstanceOf(InboxFilter.class);
            assertThat(andFilter.getFilters().get(1)).isInstanceOf(OrFilter.class);
            assertThat(andFilter.getFilters().get(2)).isInstanceOf(NotFilter.class);
        }
    }
}
