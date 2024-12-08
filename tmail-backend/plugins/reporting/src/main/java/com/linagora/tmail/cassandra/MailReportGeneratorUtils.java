package com.linagora.tmail.cassandra;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

public class MailReportGeneratorUtils {
    public static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * Generate a list of year-month partitions between two dates.
     * @return a list of year-month partitions, formatted as "yyyyMM".
     * Eg: generateYearMonthPartitions(LocalDate.parse("2021-01-01"), LocalDate.parse("2021-03-01")) returns ["202101", "202102", "202103"]
     */
    public static List<String> generateYearMonthPartitions(LocalDate startDate, LocalDate endDate) {
        return Stream.iterate(startDate,
                date -> compareToMonth(date, endDate) <= 0,
                date -> date.plusMonths(1))
            .map(YEAR_MONTH_FORMATTER::format)
            .toList();
    }

    private static int compareToMonth(LocalDate date1, LocalDate date2) {
        int yearComparison = Integer.compare(date1.getYear(), date2.getYear());
        if (yearComparison != 0) {
            return yearComparison;
        }
        return Integer.compare(date1.getMonthValue(), date2.getMonthValue());
    }
}
