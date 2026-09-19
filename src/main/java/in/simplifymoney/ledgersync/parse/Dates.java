package in.simplifymoney.ledgersync.parse;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Bank SMS carry a local date and time and no timezone.
 *
 * The customer, the bank and the branch are all in India,
 * so bank timestamps are interpreted as IST.
 */
public final class Dates {

    private Dates() {}

    public static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

    private static final List<DateTimeFormatter> SMS_FORMATS = List.of(

            // 28-07-26 08:59
            DateTimeFormatter.ofPattern(
                    "dd-MM-yy HH:mm",
                    Locale.ENGLISH),

            // 28/07/2026 08:59
            DateTimeFormatter.ofPattern(
                    "dd/MM/yyyy HH:mm",
                    Locale.ENGLISH),

            // 28 Jul 26 08:59
            DateTimeFormatter.ofPattern(
                    "dd MMM yy HH:mm",
                    Locale.ENGLISH),

            // 28-Jul-2026 08:59
            DateTimeFormatter.ofPattern(
                    "dd-MMM-yyyy HH:mm",
                    Locale.ENGLISH),

            // 28-Jul-26 08:59
            DateTimeFormatter.ofPattern(
                    "dd-MMM-yy HH:mm",
                    Locale.ENGLISH),

            // 28 Jul 2026 08:59
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy HH:mm",
                    Locale.ENGLISH)
    );

    /**
     * Parse a local date-time written by a bank SMS as IST.
     *
     * Returns null when the supplied value does not match
     * any supported bank date-time format.
     */
    public static OffsetDateTime ist(String dateAndTime) {
        if (dateAndTime == null || dateAndTime.isBlank()) {
            return null;
        }

        String value = dateAndTime.trim();

        for (DateTimeFormatter formatter : SMS_FORMATS) {
            try {
                LocalDateTime localDateTime =
                        LocalDateTime.parse(value, formatter);

                return localDateTime.atOffset(IST);

            } catch (DateTimeParseException ignored) {
                // Try the next supported format.
            }
        }

        return null;
    }
}