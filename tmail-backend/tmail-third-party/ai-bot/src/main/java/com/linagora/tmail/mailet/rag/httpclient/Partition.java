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

package com.linagora.tmail.mailet.rag.httpclient;

import org.apache.james.core.Username;

import com.google.common.base.Preconditions;

public record Partition(String partitionName) {

    public static class Factory {
        private final String pattern;

        private Factory(String pattern) {
            this.pattern = pattern;
        }

        public static Factory fromPattern(String pattern) {
            return new Factory(pattern);
        }

        public Partition forUsername(Username username) {
            Preconditions.checkArgument(username.hasDomainPart(), "Username must have a domain part for RAG partitioning");

            String localPart = username.getLocalPart();
            String domainName = username.getDomainPart().get().asString();
            return Partition.fromPattern(pattern, localPart, domainName);
        }
    }

    public static Partition fromPattern(String pattern, String localPart, String domainName) {
        Preconditions.checkArgument(pattern.contains("{localPart}"), "Pattern must contain {localPart}");
        Preconditions.checkArgument(pattern.contains("{domainName}"), "Pattern must contain {domainName}");
        String partitionName = pattern
            .replace("{localPart}", localPart)
            .replace("{domainName}", domainName);
        return new Partition(partitionName);
    }

}