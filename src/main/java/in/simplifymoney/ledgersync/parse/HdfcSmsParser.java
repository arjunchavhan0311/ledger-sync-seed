package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HDFC Bank SMS parser.
 *
 * The corpus contains:
 *
 * 1. Older single-line messages
 * 2. Newer multi-line messages
 * 3. HDFC card messages
 *
 * All monetary extraction is delegated to Amounts so that transaction
 * amounts and available balances/limits are handled consistently.
 */
public final class HdfcSmsParser implements MessageParser {

    public static final String SENDER = "AD-HDFCBK-S";

    /*
     * Older HDFC format:
     *
     * Rs.100.00 debited from a/c **4821
     * on 28-07-26 at 08:59 to AMAZON.
     *
     * Also supports "credited to" and "by".
     */
    private static final Pattern V1 = Pattern.compile(
            "(?<dir>debited\\s+from|credited\\s+to)"
                    + "\\s+a/c\\s+\\*\\*(?<acct>\\d{4})"
                    + "\\s+on\\s+"
                    + "(?<when>\\d{2}-\\d{2}-\\d{2}\\s+at\\s+\\d{2}:\\d{2})"
                    + "\\s+(?:to|by)\\s+"
                    + "(?<merchant>[^.]+?)\\.",
            Pattern.CASE_INSENSITIVE);

    /*
     * Newer HDFC format:
     *
     * Sent ...
     * To: MERCHANT
     * On: 28 Jul 26 08:59
     * A/c: XX4821
     */
    private static final Pattern V2 = Pattern.compile(
            "^\\s*(?<dir>Sent|Received)"
                    + ".*?\\R"
                    + "(?:To|From):\\s*(?<merchant>.+?)\\R"
                    + "On:\\s*(?<when>\\d{2}\\s+[A-Za-z]{3}\\s+\\d{2}\\s+\\d{2}:\\d{2})\\R"
                    + "A/c:\\s*XX(?<acct>\\d{4})",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /*
     * HDFC card format:
     *
     * ... spent on HDFC Bank Card x3310 at MERCHANT
     * on 28-07-26 08:59.
     */
    private static final Pattern CARD = Pattern.compile(
            "spent\\s+on\\s+HDFC\\s+Bank\\s+Card\\s+x(?<acct>\\d{4})"
                    + "\\s+at\\s+(?<merchant>.+?)"
                    + "\\s+on\\s+"
                    + "(?<when>\\d{2}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})\\.",
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

        String body = m.body();

        /*
         * Try the older HDFC format.
         */
        Matcher v1 = V1.matcher(body);

        if (v1.find()) {

            Direction direction =
                    v1.group("dir")
                            .toLowerCase()
                            .startsWith("debited")
                            ? Direction.DEBIT
                            : Direction.CREDIT;

            String when = v1.group("when")
                    .replaceFirst(
                            "(?i)\\s+at\\s+",
                            " ");

            return build(
                    m,
                    v1.group("acct"),
                    when,
                    direction,
                    v1.group("merchant"));
        }

        /*
         * Try the newer multi-line HDFC format.
         */
        Matcher v2 = V2.matcher(body);

        if (v2.find()) {

            Direction direction =
                    "Sent".equalsIgnoreCase(v2.group("dir"))
                            ? Direction.DEBIT
                            : Direction.CREDIT;

            return build(
                    m,
                    v2.group("acct"),
                    v2.group("when"),
                    direction,
                    v2.group("merchant"));
        }

        /*
         * Try HDFC card messages.
         *
         * Card spending is always a debit.
         */
        Matcher card = CARD.matcher(body);

        if (card.find()) {

            return build(
                    m,
                    card.group("acct"),
                    card.group("when"),
                    Direction.DEBIT,
                    card.group("merchant"));
        }

        return Optional.empty();
    }

    private Optional<ParsedTxn> build(
            RawMessage m,
            String account,
            String when,
            Direction direction,
            String merchant) {

        BigDecimal amount = Amounts.first(m.body());

        if (amount == null) {
            return Optional.empty();
        }

        OffsetDateTime occurredAt = Dates.ist(when);

        if (occurredAt == null) {
            return Optional.empty();
        }

        String cleanMerchant = cleanMerchant(merchant);

        if (cleanMerchant.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new ParsedTxn(
                account,
                occurredAt,
                direction,
                amount,
                cleanMerchant,
                Amounts.statedBalance(m.body()),
                m.messageId()
        ));
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