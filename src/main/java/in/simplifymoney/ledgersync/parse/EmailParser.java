package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailParser implements MessageParser {

    private static final Pattern TRANSACTION = Pattern.compile(
            "Your\\s+account\\s+ending\\s+(?<acct>\\d{4})"
                    + "\\s+has\\s+been\\s+"
                    + "(?<dir>debited|credited)"
                    + "\\s+with\\s+"
                    + "(?:Rs\\.?|INR)\\s*"
                    + "(?<amount>[0-9,]+(?:\\.[0-9]{1,2})?)"
                    + "\\s*\\.?"
                    + "\\s*"
                    + "Merchant\\s*/\\s*Remarks\\s*:\\s*"
                    + "(?<merchant>[^\\r\\n]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMAIL_DATE = Pattern.compile(
            "^Date:\\s*"
                    + "\\w{3},\\s*"
                    + "(?<date>\\d{2}\\s+\\w{3}\\s+\\d{4}"
                    + "\\s+\\d{2}:\\d{2}:\\d{2}"
                    + "\\s+[+-]\\d{4})",
            Pattern.CASE_INSENSITIVE
                    | Pattern.MULTILINE);

    private static final DateTimeFormatter EMAIL_DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy HH:mm:ss xx",
                    java.util.Locale.ENGLISH);

    @Override
    public boolean supports(RawMessage m) {
        return m != null
                && "email".equalsIgnoreCase(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {

        if (m == null
                || m.body() == null
                || m.body().isBlank()) {

            return Optional.empty();
        }

        Matcher transaction =
                TRANSACTION.matcher(m.body());

        if (!transaction.find()) {
            return Optional.empty();
        }

        Direction direction =
                "debited".equalsIgnoreCase(
                        transaction.group("dir"))
                        ? Direction.DEBIT
                        : Direction.CREDIT;

        BigDecimal amount;

        try {

            amount = new BigDecimal(
                    transaction.group("amount")
                            .replace(",", "")
            ).setScale(2);

        } catch (NumberFormatException e) {

            return Optional.empty();
        }

        String merchant =
                transaction.group("merchant")
                        .trim();

        if (merchant.isEmpty()) {
            return Optional.empty();
        }

        OffsetDateTime occurredAt =
                parseEmailDate(m.body());

        if (occurredAt == null) {
            return Optional.empty();
        }

        return Optional.of(
                new ParsedTxn(
                        transaction.group("acct"),
                        occurredAt,
                        direction,
                        amount,
                        merchant,
                        null,
                        m.messageId()
                )
        );
    }

    private static OffsetDateTime parseEmailDate(
            String body) {

        Matcher matcher =
                EMAIL_DATE.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        try {

            return OffsetDateTime.parse(
                    matcher.group("date"),
                    EMAIL_DATE_FORMAT);

        } catch (DateTimeParseException e) {

            return null;
        }
    }
}