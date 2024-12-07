package com.linagora.tmail.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MailReportGeneratorUtilsTest {

    record DateArgumentSample(LocalDate start, LocalDate end, List<String> datePartitionsExpected) {
    }

    private static Stream<Arguments> dateTestSample() {
        return Stream.of(
            Arguments.arguments(new DateArgumentSample(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-01-01"), List.of("202101"))),
            Arguments.arguments(new DateArgumentSample(LocalDate.parse("2021-01-01"), LocalDate.parse("2020-01-01"), List.of())),
            Arguments.arguments(new DateArgumentSample(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-01-02"), List.of("202101"))),
            Arguments.arguments(new DateArgumentSample(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-02-01"), List.of("202101", "202102"))),
            Arguments.arguments(new DateArgumentSample(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-03-01"), List.of("202101", "202102", "202103"))),
            Arguments.arguments(new DateArgumentSample(LocalDate.parse("2024-12-01"), LocalDate.parse("2025-01-01"), List.of("202412", "202501"))));
    }

    @MethodSource("dateTestSample")
    @ParameterizedTest
    void generateYearMonthPartitionsShouldNotFailWhenEmpty(DateArgumentSample sample) {
        assertThat(MailReportGeneratorUtils.generateYearMonthPartitions(sample.start, sample.end))
            .containsExactlyElementsOf(sample.datePartitionsExpected());
    }
}
