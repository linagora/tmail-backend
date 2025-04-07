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

package com.linagora.calendar.restapi.routes;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import jakarta.inject.Inject;

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.base.Preconditions;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class AvatarRoute implements JMAPRoutes {
    private final MetricFactory metricFactory;

    @Inject
    public AvatarRoute(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    Endpoint endpoint() {
        return Endpoint.ofFixedPath(HttpMethod.GET, "/api/avatars");
    }

    @Override
    public Stream<JMAPRoute> routes() {
        return Stream.of(
            JMAPRoute.builder()
                .endpoint(endpoint())
                .action((req, res) -> Mono.from(metricFactory.decoratePublisherWithTimerMetric(this.getClass().getSimpleName(), generateAvatar(req, res))))
                .corsHeaders());
    }

    Mono<Void> generateAvatar(HttpServerRequest request, HttpServerResponse response) {
        String email = extractEmail(request);
        Preconditions.checkArgument(!email.isEmpty(), "Empty email ");

        return Mono.fromCallable(() ->  generateLetterImage(email.toUpperCase(Locale.US).charAt(0)))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(data -> response
                    .status(200)
                    .header(HttpHeaderNames.CONTENT_TYPE, "image/png")
                    .header("Content-Length", String.valueOf(data.length))
                    .header("Cache-Control", "max-age=1800, public")
                    .sendByteArray(Mono.just(data))
                .then());
    }

    static String extractEmail(HttpServerRequest request) {
        return new QueryStringDecoder(request.uri()).parameters().get("email")
            .stream()
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException("Request for avatar is missing email query parameter"));
    }

    public static byte[] generateLetterImage(char letter) {
        int width = 48;
        int height = 48;

        // Create an image with an alpha channel (transparency support)
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Enable anti-aliasing for smoother text rendering
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background color: #00796B (teal)
        g.setColor(new Color(0x00796B));
        g.fillRect(0, 0, width, height);

        // Set font and color for the letter
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 36);
        g.setFont(font);
        g.setColor(Color.WHITE);

        // Get font metrics to center the text
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(String.valueOf(letter));
        int textHeight = fm.getAscent();

        // Calculate position to center the letter
        int x = (width - textWidth) / 2;
        int y = (height + textHeight) / 2 - 5;

        // Draw the letter
        g.drawString(String.valueOf(letter), x, y);
        g.dispose();

        // Convert image to byte array
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
