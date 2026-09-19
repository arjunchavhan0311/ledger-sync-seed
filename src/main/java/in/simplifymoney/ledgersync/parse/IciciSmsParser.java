package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for ICICI Bank SMS messages.
 *
 * The corpus contains more than one ICICI SMS format, so both formats
 * are handled here.
 */
public final class IciciSmsParser implements MessageParser {

    public static final String SENDER = "VM-ICICIB-T";

    /*
     * Format 1:
     *
     * Dear Customer, Acct XX4821 is debited with INR 100.00
     * on 28/07/2026 08:59. Info: AMAZON.
     *
     * The amount is captured explicitly so that the available balance
     * cannot accidentally be interpreted as the transaction amount.
     */
    private static final Pattern V1 = Pattern.compile(
            "Acct\\s+XX(?<acct>\\d{4})"
                    + "\\s+is\\s+"
                    + "(?<dir>debited|credited)"
                    + "\\s+with\\s+"
                    + "(?:INR|Rs\\.?)\\s*"
                    + "(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?)"
                    + ".*?\\s+on\\s+"
                    + "(?<when>\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2})"
                    + "\\.\\s*"
                    + "Info:\\s*"
                    + "(?<merchant>[^.]+?)\\.",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /*
     * Format 2:
     *
     * ICICI Bank Acct XX9075 Dr INR 89.01
     * on 28-Jul-2026 08:59; APOLLO PHARMACY ref no ...
     */
    private static final Pattern V2 = Pattern.compile(
            "ICICI\\s+Bank\\s+Acct\\s+XX(?<acct>\\d{4})"
                    + "\\s+"
                    + "(?<dir>Dr|Cr)"
                    + "\\s+"
                    + "(?:INR|Rs\\.?)\\s*"
                    + "(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?)"
                    + "\\s+on\\s+"
                    + "(?<when>\\d{2}-[A-Za-z]{3}-\\d{4}\\s+\\d{2}:\\d{2})"
                    + "\\s*;\\s*"
                    + "(?<merchant>.+?)"
                    + "\\s+ref\\s+no",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public boolean supports(RawMessage m) {
        return m != null
                && "sms".equalsIgnoreCase(m.channel())
                && SENDER.equalsIgnoreCase(m.sender());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {

        if (m == null || m.body() == null || m.body().isBlank()) {
            return Optional.empty();
        }

        /*
         * Try the first known ICICI format.
         */
        Matcher v1 = V1.matcher(m.body());

        if (v1.find()) {
            return parseV1(m, v1);
        }

        /*
         * Try the second known ICICI format.
         */
        Matcher v2 = V2.matcher(m.body());

        if (v2.find()) {
            return parseV2(m, v2);
        }

        return Optional.empty();
    }

    private Optional<ParsedTxn> parseV1(
            RawMessage m,
            Matcher matcher) {

        BigDecimal amount = parseAmount(matcher.group("amount"));

        if (amount == null) {
            return Optional.empty();
        }

        OffsetDateTime occurredAt =
                Dates.ist(matcher.group("when"));

        if (occurredAt == null) {
            return Optional.empty();
        }

        Direction direction =
                "debited".equalsIgnoreCase(matcher.group("dir"))
                        ? Direction.DEBIT
                        : Direction.CREDIT;

        String merchant = cleanMerchant(
                matcher.group("merchant"));

        if (merchant.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ParsedTxn(
                matcher.group("acct"),
                occurredAt,
                direction,
                amount,
                merchant,
                Amounts.statedBalance(m.body()),
                m.messageId()
        ));
    }

    private Optional<ParsedTxn> parseV2(
            RawMessage m,
            Matcher matcher) {

        BigDecimal amount = parseAmount(matcher.group("amount"));

        if (amount == null) {
            return Optional.empty();
        }

        OffsetDateTime occurredAt =
                Dates.ist(matcher.group("when"));

        if (occurredAt == null) {
            return Optional.empty();
        }

        Direction direction =
                "Dr".equalsIgnoreCase(matcher.group("dir"))
                        ? Direction.DEBIT
                        : Direction.CREDIT;

        String merchant = cleanMerchant(
                matcher.group("merchant"));

        if (merchant.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ParsedTxn(
                matcher.group("acct"),
                occurredAt,
                direction,
                amount,
                merchant,
                Amounts.statedBalance(m.body()),
                m.messageId()
        ));
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return new BigDecimal(
                    raw.replace(",", "")
            ).setScale(2);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cleanMerchant(String merchant) {
        if (merchant == null) {
            return "";
        }

        return merchant
                .replaceAll("\\s+", " ")
                .trim();
    }
}