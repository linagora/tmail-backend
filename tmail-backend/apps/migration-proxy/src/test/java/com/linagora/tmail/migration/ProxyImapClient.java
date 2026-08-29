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

package com.linagora.tmail.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A raw IMAP client for the proxy tests: the proxy speaks a hand written subset of IMAP, so a line
 * oriented socket is a closer reading of what is on the wire than a full blown client library.
 */
public class ProxyImapClient implements AutoCloseable {
    private static final int READ_TIMEOUT_MS = 60_000;

    private final Socket socket;
    private final BufferedReader reader;

    public ProxyImapClient(int port) throws IOException {
        this.socket = new Socket("127.0.0.1", port);
        this.socket.setSoTimeout(READ_TIMEOUT_MS);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        assertThat(readLine()).startsWith("* OK");
    }

    public void send(String line) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public String readLine() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new AssertionError("The proxy closed the connection");
        }
        return line;
    }

    /**
     * The tagged response, discarding the untagged lines that precede it.
     */
    public String tagged(String tag) throws IOException {
        return untilTagged(tag).getLast();
    }

    /**
     * Every line up to and including the tagged response.
     */
    public List<String> untilTagged(String tag) throws IOException {
        List<String> lines = new ArrayList<>();
        while (lines.stream().noneMatch(line -> line.startsWith(tag + " "))) {
            lines.add(readLine());
        }
        return lines;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
