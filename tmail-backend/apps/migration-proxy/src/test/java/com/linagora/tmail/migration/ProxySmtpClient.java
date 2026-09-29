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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A raw SMTP client for the submission proxy tests: what is on the wire matters here (pipelining, byte
 * exact relaying), so a line oriented socket is a closer reading than a full blown client library.
 */
public class ProxySmtpClient implements AutoCloseable {
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final String HOST = "127.0.0.1";

    public static ProxySmtpClient plain(int port) throws IOException {
        return new ProxySmtpClient(new Socket(HOST, port));
    }

    public static ProxySmtpClient implicitTls(int port) throws Exception {
        SSLSocket socket = (SSLSocket) trustAll().getSocketFactory().createSocket(HOST, port);
        socket.startHandshake();
        return new ProxySmtpClient(socket);
    }

    public static String plainResponse(String username, String password) {
        return encode('\0' + username + '\0' + password);
    }

    public static String encode(String payload) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private Socket socket;
    private BufferedReader reader;

    private ProxySmtpClient(Socket socket) throws IOException {
        use(socket);
    }

    private void use(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(READ_TIMEOUT_MS);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    public List<String> greeting() throws IOException {
        List<String> greeting = readReply();
        assertThat(greeting.getLast()).startsWith("220 ");
        return greeting;
    }

    public void send(String line) throws IOException {
        sendRaw(line + "\r\n");
    }

    /**
     * Written in a single call so that several commands land in the same TCP segment.
     */
    public void sendRaw(String payload) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(payload.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public List<String> command(String line) throws IOException {
        send(line);
        return readReply();
    }

    /**
     * The last line of the reply, which carries the reply code.
     */
    public String reply(String line) throws IOException {
        return command(line).getLast();
    }

    /**
     * Every line of the next (possibly multi-line) reply.
     */
    public List<String> readReply() throws IOException {
        List<String> lines = new ArrayList<>();
        String line;
        do {
            line = reader.readLine();
            if (line == null) {
                throw new AssertionError("The proxy closed the connection, read so far: " + lines);
            }
            lines.add(line);
        } while (line.length() > 3 && line.charAt(3) == '-');
        return lines;
    }

    public void startTls() throws Exception {
        assertThat(reply("STARTTLS")).startsWith("220 ");
        SSLSocket tlsSocket = (SSLSocket) trustAll().getSocketFactory()
            .createSocket(socket, HOST, socket.getPort(), true);
        tlsSocket.startHandshake();
        use(tlsSocket);
    }

    /**
     * True once the proxy closed the connection; false while it is idle but still open.
     */
    public boolean isClosedByPeer(int waitMs) throws IOException {
        socket.setSoTimeout(waitMs);
        try {
            return reader.readLine() == null;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return true;
        } finally {
            socket.setSoTimeout(READ_TIMEOUT_MS);
        }
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
