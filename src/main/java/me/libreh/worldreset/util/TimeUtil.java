package me.libreh.worldreset.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = SECONDS_PER_MINUTE * 60;
    private static final long SECONDS_PER_DAY = SECONDS_PER_HOUR * 24;
    private static final long SECONDS_PER_WEEK = SECONDS_PER_DAY * 7;
    private static final long SECONDS_PER_MONTH = SECONDS_PER_DAY * 30;
    private static final long SECONDS_PER_YEAR = SECONDS_PER_DAY * 365;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+\\.?\\d*)([a-z]+)");

    public static long parseDuration(String input) throws NumberFormatException {
        String normalized = input.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");

        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            return parseComplexDuration(normalized);
        }
    }

    private static long parseComplexDuration(String input) {
        Matcher matcher = TIME_PATTERN.matcher(input);
        long totalSeconds = 0;

        while (matcher.find()) {
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2);
            totalSeconds += convertToSeconds(value, unit);
        }

        return totalSeconds;
    }

    private static long convertToSeconds(double value, String unit) {
        return (long) (value * switch (unit) {
            case "c" -> SECONDS_PER_YEAR * 100;
            case "y", "year", "years" -> SECONDS_PER_YEAR;
            case "mo", "month", "months" -> SECONDS_PER_MONTH;
            case "w", "week", "weeks" -> SECONDS_PER_WEEK;
            case "d", "day", "days" -> SECONDS_PER_DAY;
            case "h", "hour", "hours" -> SECONDS_PER_HOUR;
            case "m", "min", "minute", "minutes" -> SECONDS_PER_MINUTE;
            default -> 1;
        });
    }
}