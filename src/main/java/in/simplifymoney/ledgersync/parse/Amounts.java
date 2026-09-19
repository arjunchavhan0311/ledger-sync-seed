package in.simplifymoney.ledgersync.parse;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Amounts {

    private Amounts() {}

    /*
     * Supports both:
     *
     *   Rs.5
     *   Rs.99.99
     *   Rs.1249.99
     *   Rs.2,499.50
     *   INR 45000.00
     *   INR 45,000.00
     *
     * IMPORTANT:
     * The integer part starts with [0-9]+ so an unformatted amount such as
     * 45000 is not accidentally truncated to 450.
     */
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:Rs\\.?|INR)\\s*"
                    + "([0-9]+(?:,[0-9]{3})*)"
                    + "(?:\\.([0-9]{1,2}))?",
            Pattern.CASE_INSENSITIVE);

    /*
     * Explicit available balance / limit.
     *
     * Examples:
     *
     *   Avl Bal: Rs.92,213.10
     *   Available Balance: INR 45679.37
     *   BalAvl Rs 52,846.30
     *   Avl Limit: Rs.196,250.03
     */
    private static final Pattern BALANCE = Pattern.compile(
            "(?:Avl\\s*Bal|Available\\s*Balance|BalAvl|Avl\\s*Limit)"
                    + "\\s*:?\\s*"
                    + "(?:Rs\\.?|INR)\\s*"
                    + "([0-9]+(?:,[0-9]{3})*)"
                    + "(?:\\.([0-9]{1,2}))?",
            Pattern.CASE_INSENSITIVE);

    public static BigDecimal first(String body) {

        if (body == null || body.isBlank()) {
            return null;
        }

        Matcher matcher = AMOUNT.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return toDecimal(
                matcher.group(1),
                matcher.group(2));
    }

    public static BigDecimal statedBalance(String body) {

        if (body == null || body.isBlank()) {
            return null;
        }

        Matcher matcher = BALANCE.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return toDecimal(
                matcher.group(1),
                matcher.group(2));
    }

    private static BigDecimal toDecimal(
            String integerPart,
            String fractionalPart) {

        String value =
                integerPart.replace(",", "");

        if (fractionalPart == null
                || fractionalPart.isEmpty()) {

            value += ".00";

        } else if (fractionalPart.length() == 1) {

            value += "." + fractionalPart + "0";

        } else {

            value += "." + fractionalPart;
        }

        return new BigDecimal(value)
                .setScale(2);
    }
}