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

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

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

    private static Stream<Arguments> provideEntryNames() {
        return Stream.of(
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
