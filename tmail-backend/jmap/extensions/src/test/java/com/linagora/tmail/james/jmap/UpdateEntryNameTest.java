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

package com.linagora.tmail.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableList;

public class UpdateEntryNameTest {
    @ParameterizedTest
    @MethodSource("provideEntryNames")
    public void testUpdateEntryNameMethod(List<String> input, List<String> expected) {
        List<ZipUtil.ZipEntryStreamSource> streamSources = input.stream()
            .map(entryName -> new ZipUtil.ZipEntryStreamSource(InputStream.nullInputStream(), entryName)).toList();
        List<String> actual = ZipUtil.updateEntryName(streamSources).stream().map(ZipUtil.ZipEntryStreamSource::entryName).toList();
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("provideUnsafeEntryNames")
    public void sanitizeEntryNameShouldRemovePathTraversal(String input, String expected) {
        assertThat(ZipUtil.sanitizeEntryName(input)).isEqualTo(expected);
    }

    @Test
    void createZipStreamShouldSupportAnAttachmentNameLongerThanTheZipLimit() {
        assertZipOf("a".repeat(70_000) + ".pdf");
    }

    @Test
    void createZipStreamShouldSupportANonAsciiNameLongerThanTheZipLimitInBytes() {
        assertZipOf("é".repeat(40_000) + ".pdf");
    }

    @Test
    void sanitizeEntryNameShouldNotSplitSurrogatePairs() {
        String sanitized = ZipUtil.sanitizeEntryName("😀".repeat(1_000) + ".pdf");

        assertThat(sanitized)
            .endsWith(".pdf")
            .isEqualTo("😀".repeat(49) + ".pdf");
    }

    @Test
    void sanitizeEntryNameShouldDropAnOversizedExtension() {
        String sanitized = ZipUtil.sanitizeEntryName("a." + "b".repeat(70_000));

        assertThat(sanitized).isEqualTo("a." + "b".repeat(198));
    }

    @Test
    void sanitizeEntryNameShouldKeepNamesWithinTheLimit() {
        String name = "a".repeat(196) + ".pdf";

        assertThat(ZipUtil.sanitizeEntryName(name)).isEqualTo(name);
    }

    @Test
    void updateEntryNameShouldDeduplicateTruncatedNames() {
        List<ZipUtil.ZipEntryStreamSource> streamSources = Stream.of("a".repeat(300) + "1.pdf", "a".repeat(300) + "2.pdf")
            .map(entryName -> new ZipUtil.ZipEntryStreamSource(InputStream.nullInputStream(), entryName))
            .toList();

        List<String> actual = ZipUtil.updateEntryName(streamSources).stream().map(ZipUtil.ZipEntryStreamSource::entryName).toList();

        assertThat(actual).containsExactly("a".repeat(196) + ".pdf", "a".repeat(196) + "_1.pdf");
    }

    private void assertZipOf(String attachmentName) {
        List<byte[]> chunks = ZipUtil.createZipStream(ImmutableList.of(
                new ZipUtil.ZipEntryStreamSource(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)), attachmentName),
                new ZipUtil.ZipEntryStreamSource(new ByteArrayInputStream("other".getBytes(StandardCharsets.UTF_8)), "other.txt")))
            .collectList()
            .block();
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        chunks.forEach(zip::writeBytes);

        List<ZipUtil.ZipEntryData> entries = ZipUtil.readZipData(new ByteArrayInputStream(zip.toByteArray()));
        assertThat(entries).extracting(ZipUtil.ZipEntryData::content).containsExactly("content", "other");
        assertThat(entries.get(0).fileName().getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(255);
        assertThat(entries.get(0).fileName()).endsWith(".pdf");
    }

    private static Stream<Arguments> provideUnsafeEntryNames() {
        return Stream.of(
            Arguments.of("text.txt", "text.txt"),
            Arguments.of("../../zipslip.txt", "zipslip.txt"),
            Arguments.of("/etc/abs.txt", "abs.txt"),
            Arguments.of("..\\..\\win.txt", "win.txt"),
            Arguments.of("C:\\Windows\\evil.txt", "evil.txt"),
            Arguments.of("C:evil.txt", "evil.txt"),
            Arguments.of(" C:evil.txt", "evil.txt"),
            Arguments.of("C: evil.txt", "evil.txt"),
            Arguments.of("C:D:evil.txt", "evil.txt"),
            Arguments.of("C: D:evil.txt", "evil.txt"),
            Arguments.of("evil\u0000\n.txt", "evil.txt"),
            Arguments.of("..", "noname"),
            Arguments.of(".", "noname"),
            Arguments.of("dir/", "noname"),
            Arguments.of("", "noname"),
            Arguments.of("..file.txt", "..file.txt"));
    }

    private static Stream<Arguments> provideEntryNames() {
        return Stream.of(
            Arguments.of(
                ImmutableList.of("../a.txt", "/b/a.txt", "a.txt"),
                ImmutableList.of("a.txt", "a_1.txt", "a_2.txt")
            ),
            Arguments.of(
                ImmutableList.of("text1", "text2", "text3"),
                ImmutableList.of("text1", "text2", "text3")
            ),
            Arguments.of(
                ImmutableList.of("text1.txt", "text2.txt", "text3.txt"),
                ImmutableList.of("text1.txt", "text2.txt", "text3.txt")
            ),
            Arguments.of(
                ImmutableList.of("text", "text", "text"),
                ImmutableList.of("text", "text_1", "text_2")
            ),
            Arguments.of(
                ImmutableList.of("doc.pdf", "doc.pdf", "doc.pdf"),
                ImmutableList.of("doc.pdf", "doc_1.pdf", "doc_2.pdf")
            ),
            Arguments.of(
                ImmutableList.of("text", "text", "text.txt", "text", "text.txt"),
                ImmutableList.of("text", "text_1", "text.txt", "text_2", "text_1.txt")
            ),
            Arguments.of(
                ImmutableList.of("text", "text", "text_1", "text_1_1", "text_1"),
                ImmutableList.of("text", "text_1", "text_1_1", "text_1_1_1", "text_1_2")
            ),
            Arguments.of(
                ImmutableList.of("text.txt", "text.txt", "text_1.txt", "text_1_1.txt", "text_1.txt"),
                ImmutableList.of("text.txt", "text_1.txt", "text_1_1.txt", "text_1_1_1.txt", "text_1_2.txt")
            ),
            Arguments.of(
                ImmutableList.of("file.txt", "file.txt", "file", "image.jpg", "image", "doc.pdf", "file", "image.jpg"),
                ImmutableList.of("file.txt", "file_1.txt", "file", "image.jpg", "image", "doc.pdf", "file_1", "image_1.jpg")
            )
        );
    }
}
