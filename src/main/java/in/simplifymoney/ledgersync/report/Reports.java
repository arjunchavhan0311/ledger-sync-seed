package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Reporting functions for the normalized ledger.
 */
public final class Reports {

    private Reports() {}

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2);

    /**
     * Build the account-level summary.
     *
     * SPEND:
     *   Normal spending only.
     *
     * INCOME:
     *   Normal income only.
     *
     * MICRO:
     *   Small UPI spends are kept in the ledger but reported separately.
     *
     * TRANSFER:
     *   Own-account transfers are excluded from spend/income and
     *   reported separately.
     */
    public static Map<String, Object> summary(
            List<NormalizedTxn> ledger) {

        Map<String, Object> accounts = new LinkedHashMap<>();

        if (ledger == null || ledger.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accounts", accounts);
            return result;
        }

        /*
         * Keep account ordering deterministic.
         */
        for (String accountNumber : new TreeSet<>(
                ledger.stream()
                        .map(NormalizedTxn::accountLast4)
                        .filter(a -> a != null)
                        .toList())) {

            BigDecimal spend = ZERO;
            BigDecimal income = ZERO;

            BigDecimal microTotal = ZERO;
            int microCount = 0;

            BigDecimal transferredOut = ZERO;
            BigDecimal transferredIn = ZERO;

            for (NormalizedTxn txn : ledger) {

                if (!accountNumber.equals(txn.accountLast4())) {
                    continue;
                }

                Category category = txn.category();

                /*
                 * Transfers are neither spending nor income.
                 */
                if (category == Category.TRANSFER) {

                    if (txn.direction() == Direction.DEBIT) {
                        transferredOut =
                                transferredOut.add(txn.amount());
                    } else {
                        transferredIn =
                                transferredIn.add(txn.amount());
                    }

                    continue;
                }

                /*
                 * Micro spends remain individual transactions in the
                 * ledger but are rolled up in the summary.
                 */
                if (category == Category.MICRO) {

                    microCount++;

                    microTotal =
                            microTotal.add(txn.amount());

                    continue;
                }

                /*
                 * Normal spending.
                 */
                if (category == Category.SPEND) {

                    spend = spend.add(txn.amount());

                    continue;
                }

                /*
                 * Normal income.
                 */
                if (category == Category.INCOME) {

                    income = income.add(txn.amount());
                }
            }

            /*
             * This is the summary document for one account.
             *
             * IMPORTANT:
             * The account number is accountNumber.
             * The map itself is accountSummary.
             */
            Map<String, Object> accountSummary =
                    new LinkedHashMap<>();

            accountSummary.put(
                    "spend",
                    spend.setScale(2).toPlainString());

            accountSummary.put(
                    "income",
                    income.setScale(2).toPlainString());

            accountSummary.put(
                    "micro_count",
                    microCount);

            accountSummary.put(
                    "micro_total",
                    microTotal.setScale(2).toPlainString());

            accountSummary.put(
                    "transferred_out",
                    transferredOut.setScale(2).toPlainString());

            accountSummary.put(
                    "transferred_in",
                    transferredIn.setScale(2).toPlainString());

            /*
             * Correct:
             *
             * key   = account number, e.g. "4821"
             * value = summary map
             */
            accounts.put(accountNumber, accountSummary);
        }

        Map<String, Object> document =
                new LinkedHashMap<>();

        document.put("accounts", accounts);

        return document;
    }

    /**
     * Convert the normalized ledger into a document representation.
     */
    public static Map<String, Object> ledgerDocument(
            List<NormalizedTxn> ledger) {

        List<Object> rows = ledger.stream()
                .map(Reports::transactionDocument)
                .map(row -> (Object) row)
                .toList();

        Map<String, Object> document =
                new LinkedHashMap<>();

        document.put("transactions", rows);

        return document;
    }

    private static Map<String, Object> transactionDocument(
            NormalizedTxn txn) {

        Map<String, Object> row =
                new LinkedHashMap<>();

        row.put(
                "account_last4",
                txn.accountLast4());

        row.put(
                "occurred_at",
                txn.occurredAt().toString());

        row.put(
                "direction",
                txn.direction().name().toLowerCase());

        row.put(
                "amount",
                txn.amount()
                        .setScale(2)
                        .toPlainString());

        row.put(
                "category",
                txn.category().name());

        row.put(
                "merchant",
                txn.merchant());

        row.put(
                "source_message_ids",
                txn.sourceMessageIds());

        return row;
    }

    /**
     * Calculate ledger-side debit, credit and net movement.
     *
     * The authoritative bank balance is not available through this
     * method's current signature, so this method does not invent a
     * reconciliation difference.
     *
     * We will connect this to bank_balance after checking LedgerStore.
     */
    public static Map<String, Object> reconciliation(
            List<NormalizedTxn> ledger) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        Map<String, Object> accounts =
                new LinkedHashMap<>();

        if (ledger != null) {

            for (NormalizedTxn txn : ledger) {

                String accountNumber =
                        txn.accountLast4();

                if (accountNumber == null) {
                    continue;
                }

                Map<String, Object> accountResult =
                        getOrCreateAccount(
                                accounts,
                                accountNumber);

                BigDecimal debit =
                        toDecimal(
                                accountResult.get("debit"));

                BigDecimal credit =
                        toDecimal(
                                accountResult.get("credit"));

                if (txn.direction() == Direction.DEBIT) {
                    debit = debit.add(txn.amount());
                } else {
                    credit = credit.add(txn.amount());
                }

                BigDecimal net =
                        credit.subtract(debit);

                accountResult.put(
                        "debit",
                        debit.setScale(2).toPlainString());

                accountResult.put(
                        "credit",
                        credit.setScale(2).toPlainString());

                accountResult.put(
                        "net",
                        net.setScale(2).toPlainString());
            }
        }

        result.put("accounts", accounts);
        result.put("status", "ledger_only");

        return result;
    }

    private static Map<String, Object> getOrCreateAccount(
            Map<String, Object> accounts,
            String accountNumber) {

        Object existing =
                accounts.get(accountNumber);

        if (existing instanceof Map<?, ?>) {

            @SuppressWarnings("unchecked")
            Map<String, Object> existingMap =
                    (Map<String, Object>) existing;

            return existingMap;
        }

        Map<String, Object> accountResult =
                new LinkedHashMap<>();

        accountResult.put(
                "debit",
                ZERO.toPlainString());

        accountResult.put(
                "credit",
                ZERO.toPlainString());

        accountResult.put(
                "net",
                ZERO.toPlainString());

        accounts.put(
                accountNumber,
                accountResult);

        return accountResult;
    }

    private static BigDecimal toDecimal(Object value) {

        if (value == null) {
            return ZERO;
        }

        return new BigDecimal(value.toString())
                .setScale(2);
    }

    /**
     * Aggregate transactions by category.
     */
    public static Map<Category, BigDecimal> byCategory(
            List<NormalizedTxn> ledger) {

        Map<Category, BigDecimal> result =
                new LinkedHashMap<>();

        for (Category category : Category.values()) {
            result.put(category, ZERO);
        }

        if (ledger == null) {
            return result;
        }

        for (NormalizedTxn txn : ledger) {

            Category category =
                    txn.category();

            result.put(
                    category,
                    result.get(category)
                            .add(txn.amount())
                            .setScale(2));
        }

        return result;
    }
}