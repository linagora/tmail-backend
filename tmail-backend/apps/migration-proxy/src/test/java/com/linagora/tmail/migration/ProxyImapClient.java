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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A raw IMAP client for the proxy tests: the proxy speaks a hand written subset of IMAP, so a line
 * oriented socket is a closer reading of what is on the wire than a full blown client library.
 */
public class ProxyImapClient implements AutoCloseable {
    private static final int READ_TIMEOUT_MS = 60_000;

    private static final String HOST = "127.0.0.1";

    private Socket socket;
    private BufferedReader reader;

    public ProxyImapClient(int port) throws IOException {
        use(new Socket(HOST, port));
        assertThat(readLine()).startsWith("* OK");
    }

    /**
     * Negotiates STARTTLS, trusting the self-signed test certificate of the proxy.
     */
    public void startTls(String tag) throws Exception {
        send(tag + " STARTTLS");
        assertThat(tagged(tag)).startsWith(tag + " OK");
        SSLSocket tlsSocket = (SSLSocket) trustAll().getSocketFactory()
            .createSocket(socket, HOST, socket.getPort(), true);
        tlsSocket.startHandshake();
        use(tlsSocket);
    }

    private void use(Socket newSocket) throws IOException {
        this.socket = newSocket;
        this.socket.setSoTimeout(READ_TIMEOUT_MS);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
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

    private static SSLContext trustAll() throws Exception {
        TrustManager trustAll = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {trustAll}, new SecureRandom());
        return context;
    }
}
