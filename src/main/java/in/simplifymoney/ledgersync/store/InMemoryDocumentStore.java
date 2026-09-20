package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of the document-store access patterns.
 *
 * This implementation keeps the document-store contract independent from
 * MongoDB/DynamoDB and is useful for local verification and tests.
 */
public final class InMemoryDocumentStore implements DocumentStore {

    private final List<NormalizedTxn> transactions = new ArrayList<>();

    @Override
    public synchronized void save(NormalizedTxn txn) {
        if (txn == null) {
            return;
        }

        // Idempotency: do not store the same transaction twice.
        for (NormalizedTxn existing : transactions) {
            if (sameTransaction(existing, txn)) {
                return;
            }
        }

        transactions.add(txn);
    }

    @Override
    public synchronized List<NormalizedTxn> forAccountMonth(
            String accountLast4,
            YearMonth month) {

        if (accountLast4 == null || month == null) {
            return List.of();
        }

        return transactions.stream()
                .filter(t -> accountLast4.equals(t.accountLast4()))
                .filter(t -> month.equals(YearMonth.from(t.occurredAt())))
                .sorted(Comparator.comparing(
                        NormalizedTxn::occurredAt).reversed())
                .toList();
    }

    @Override
    public synchronized Map<Category, BigDecimal> categoryTotals(
            String accountLast4) {

        Map<Category, BigDecimal> totals = new LinkedHashMap<>();

        if (accountLast4 == null) {
            return totals;
        }

        for (NormalizedTxn txn : transactions) {
            if (!accountLast4.equals(txn.accountLast4())) {
                continue;
            }

            totals.merge(
                    txn.category(),
                    txn.amount().setScale(2),
                    BigDecimal::add
            );
        }

        return totals;
    }

    @Override
    public synchronized Optional<NormalizedTxn> byMessageId(
            String messageId) {

        if (messageId == null || messageId.isBlank()) {
            return Optional.empty();
        }

        for (NormalizedTxn txn : transactions) {
            if (txn.sourceMessageIds().contains(messageId)) {
                return Optional.of(txn);
            }
        }

        return Optional.empty();
    }

    private static boolean sameTransaction(
            NormalizedTxn a,
            NormalizedTxn b) {

        return a.accountLast4().equals(b.accountLast4())
                && a.occurredAt().toInstant()
                        .equals(b.occurredAt().toInstant())
                && a.direction() == b.direction()
                && a.amount().compareTo(b.amount()) == 0
                && normalize(a.merchant()).equals(normalize(b.merchant()))
                && a.category() == b.category();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ")
                        .toUpperCase();
    }
}