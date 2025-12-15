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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationRuntimeException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailbox.SystemMailboxesProvider;

import com.google.common.base.Strings;

public class MessageFilterParser {
    private final SystemMailboxesProvider systemMailboxesProvider;

    public MessageFilterParser(SystemMailboxesProvider systemMailboxesProvider) {
        this.systemMailboxesProvider = systemMailboxesProvider;
    }

    public MessageFilter parse(HierarchicalConfiguration<ImmutableNode> config) {
        try {
            HierarchicalConfiguration<ImmutableNode> filterConfig = config.configurationAt("listener.configuration.filter");

            if (filterConfig.isEmpty()) {
                return MessageFilter.ALL;
            }

            return parseFilter(filterConfig);
        } catch (ConfigurationRuntimeException e) {
            return MessageFilter.ALL;
        }
    }

    private MessageFilter parseFilter(HierarchicalConfiguration<ImmutableNode> config) {
        List<HierarchicalConfiguration<ImmutableNode>> children = config.childConfigurationsAt("");

        if (children.isEmpty()) {
            throw new IllegalArgumentException("Filter configuration must have at least one child element");
        }

        if (children.size() > 1) {
            throw new IllegalArgumentException("Filter configuration must have exactly one root element");
        }

        HierarchicalConfiguration<ImmutableNode> root = children.get(0);
        return parseNode(root);
    }

    private MessageFilter parseNode(HierarchicalConfiguration<ImmutableNode> node) {
        return switch (node.getRootElementName()) {
            case "and" -> parseAndFilter(node);
            case "or" -> parseOrFilter(node);
            case "not" -> parseNotFilter(node);
            case "isInINBOX" -> new InboxFilter(systemMailboxesProvider);
            case "isMainRecipient" -> new MainRecipientFilter();
            case "isAutoSubmitted" -> new AutoSubmittedFilter();
            case "hasHeader" -> parseHasHeaderFilter(node);
            default -> throw new IllegalArgumentException("Unknown filter type: " + node.getRootElementName());
        };
    }

    private AndFilter parseAndFilter(HierarchicalConfiguration<ImmutableNode> node) {
        List<MessageFilter> childFilters = new ArrayList<>();
        for (HierarchicalConfiguration<ImmutableNode> child : node.childConfigurationsAt("")) {
            childFilters.add(parseNode(child));
        }
        return new AndFilter(childFilters);
    }

    private OrFilter parseOrFilter(HierarchicalConfiguration<ImmutableNode> node) {
        List<MessageFilter> childFilters = new ArrayList<>();
        for (HierarchicalConfiguration<ImmutableNode> child : node.childConfigurationsAt("")) {
            childFilters.add(parseNode(child));
        }
        return new OrFilter(childFilters);
    }

    private NotFilter parseNotFilter(HierarchicalConfiguration<ImmutableNode> node) {
        List<HierarchicalConfiguration<ImmutableNode>> children = node.childConfigurationsAt("");
        if (children.size() != 1) {
            throw new IllegalArgumentException("Not filter must have exactly one child filter");
        }
        return new NotFilter(parseNode(children.get(0)));
    }

    private HasHeaderFilter parseHasHeaderFilter(HierarchicalConfiguration<ImmutableNode> node) {
        String headerName = node.getString("[@name]");
        if (Strings.isNullOrEmpty(headerName)) {
            throw new IllegalArgumentException("hasHeader filter requires 'name' attribute");
        }

        String value = node.getString("[@value]", null);
        return new HasHeaderFilter(headerName, Optional.ofNullable(value));
    }
}
