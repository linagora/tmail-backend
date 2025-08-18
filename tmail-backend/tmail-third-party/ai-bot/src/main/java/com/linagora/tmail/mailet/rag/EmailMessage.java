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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;



public class EmailMessage {

    private static final CharMatcher NEW_LINE = CharMatcher.anyOf("\n");
    private static final  CharMatcher CRLF = CharMatcher.anyOf("\r\n");
    private static final Pattern SIG_REGEX = Pattern.compile("(—|--|__|-\\w)|(^Sent from my (\\w+\\s*){1,3})");
    private static final Pattern QUOTE_HDR_REGEX = Pattern.compile("^:etorw.*nO");
    private static final Pattern MULTI_QUOTE_HDR_REGEX = Pattern.compile("(?!On.*On\\s.+?wrote:)(On\\s(.+?)wrote:)");
    private static final Pattern QUOTED_REGEX = Pattern.compile("(>+)");
    private final String text;
    private List<Fragment> fragments = Lists.newArrayList();
    private boolean foundVisible = false;
    private Fragment fragment;

    public EmailMessage(String text) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(text));
        this.text = CRLF.replaceFrom(text, "\n");
    }

    public static EmailMessage read(String content) {
        EmailMessage message = new EmailMessage(content);
        message.read();
        return message;
    }

    public static String parseReply(String content) {
        EmailMessage message = read(content);
        return message.getReply();
    }

    public List<Fragment> getFragments() {
        return fragments;
    }

    public String getReply() {
        return fragments.stream()
            .filter(f -> !(f.isHidden() || f.isQuoted()))
            .map(f -> f.getContent())
            .collect(Collectors.joining("\n"));
    }

    public void read() {
        String workingText = text;
        Matcher multiQuote = Pattern.compile(MULTI_QUOTE_HDR_REGEX.pattern(), Pattern.MULTILINE | Pattern.DOTALL)
            .matcher(workingText);

        if (multiQuote.find()) {
            String newQuoteHeader = NEW_LINE.replaceFrom(multiQuote.group(), "");
            workingText = Pattern.compile(MULTI_QUOTE_HDR_REGEX.pattern(), Pattern.DOTALL).matcher(workingText).replaceAll(
                newQuoteHeader);
        }

        Lists.reverse(Splitter.on('\n').splitToList(workingText))
            .stream()
            .forEach(l -> scanLine(l));

        finishFragment();

        fragments = ImmutableList.copyOf(Lists.reverse(fragments));
    }

    private void scanLine(String line) {
        line = NEW_LINE.trimFrom(line);
        if (SIG_REGEX.matcher(line).lookingAt()) {
            line = NEW_LINE.trimLeadingFrom(line);
        }
        boolean isQuoted = QUOTED_REGEX.matcher(line).lookingAt();

        if (fragment != null && isStringEmpty(line)) {
            if (SIG_REGEX.matcher(fragment.lines.get(fragment.lines.size() - 1)).lookingAt()) {
                fragment.setSignature(true);
                finishFragment();
            }
        }

        if (fragment != null &&
            ((fragment.isQuoted() == isQuoted) ||
                (fragment.isQuoted() && (quoteHeader(line) || isStringEmpty(line))))) {
            fragment.lines.add(line);
        } else {
            finishFragment();
            fragment = new Fragment(isQuoted, line);
        }

    }

    private boolean quoteHeader(String line) {
        String reversed = new StringBuffer(line).reverse().toString();
        return QUOTE_HDR_REGEX.matcher(reversed).lookingAt();
    }

    private void finishFragment() {
        if (fragment != null) {
            fragment.finish();
            if (!foundVisible) {
                if (fragment.isQuoted() ||
                    fragment.isSignature() ||
                    isStringEmpty(fragment.getContent())) {
                    fragment.setHidden(true);
                } else {
                    foundVisible = true;
                }
            }
            fragments.add(fragment);
        }

        fragment = null;
    }

    private boolean isStringEmpty(String content) {
        return CharMatcher.whitespace().trimFrom(content).isEmpty();
    }

    public static class Fragment {

        private boolean signature;
        private boolean hidden;
        private boolean quoted;
        private String content;
        private List<String> lines = Lists.newArrayList();

        public Fragment(boolean quoted, String firstLine) {
            this.quoted = quoted;
            this.lines.add(firstLine);
        }

        public String getContent() {
            return content;
        }

        public boolean isSignature() {
            return signature;
        }

        public void setSignature(boolean signature) {
            this.signature = signature;
        }

        public boolean isHidden() {
            return hidden;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        public boolean isQuoted() {
            return quoted;
        }

        public void finish() {
            content = CharMatcher.whitespace().trimFrom(Lists.reverse(lines).stream().collect(Collectors.joining("\n")));
            lines = ImmutableList.of();
        }
    }

}