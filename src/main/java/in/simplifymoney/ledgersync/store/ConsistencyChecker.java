package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares transactions stored in SQL and the document store.
 *
 * The check uses source message ids as the stable cross-store identity.
 * This allows it to detect field-level changes, not just row-count
 * differences.
 */
public final class ConsistencyChecker {

    private final SqlLedgerStore sql;
    private final DocumentStore documents;

    public ConsistencyChecker(
            SqlLedgerStore sql,
            DocumentStore documents) {
        this.sql = sql;
        this.documents = documents;
    }

    public List<Divergence> check() {
        List<Divergence> divergences = new ArrayList<>();

        List<NormalizedTxn> sqlRows = sql.all();
        Set<String> checkedDocumentKeys = new HashSet<>();

        for (NormalizedTxn sqlTxn : sqlRows) {

            if (sqlTxn.sourceMessageIds() == null
                    || sqlTxn.sourceMessageIds().isEmpty()) {

                divergences.add(new Divergence(
                        "SQL transaction has no source message id",
                        describe(sqlTxn),
                        "not identifiable"
                ));
                continue;
            }

            NormalizedTxn documentTxn = null;

            for (String messageId : sqlTxn.sourceMessageIds()) {
                var found = documents.byMessageId(messageId);

                if (found.isPresent()) {
                    documentTxn = found.get();
                    break;
                }
            }

            if (documentTxn == null) {
                divergences.add(new Divergence(
                        "Missing transaction in document store",
                        describe(sqlTxn),
                        "missing"
                ));
                continue;
            }

            checkedDocumentKeys.add(transactionKey(documentTxn));

            if (!sameTransaction(sqlTxn, documentTxn)) {
                divergences.add(new Divergence(
                        "Transaction differs between SQL and document store",
                        describe(sqlTxn),
                        describe(documentTxn)
                ));
            }
        }

        /*
         * The DocumentStore interface does not expose an all() operation,
         * so unknown documents cannot be enumerated directly.
         *
         * We therefore perform the strongest check available through the
         * required access patterns: every SQL transaction must have a
         * matching document and matching fields.
         */

        return divergences;
    }

    private static boolean sameTransaction(
            NormalizedTxn a,
            NormalizedTxn b) {

        return safeEquals(a.accountLast4(), b.accountLast4())
                && a.occurredAt().toInstant()
                    .equals(b.occurredAt().toInstant())
                && a.direction() == b.direction()
                && a.amount().compareTo(b.amount()) == 0
                && safeEquals(
                        normalize(a.merchant()),
                        normalize(b.merchant()))
                && a.category() == b.category()
                && safeEquals(
                        new HashSet<>(a.sourceMessageIds()),
                        new HashSet<>(b.sourceMessageIds()));
    }

    private static String transactionKey(NormalizedTxn txn) {
        return txn.accountLast4()
                + "|"
                + txn.occurredAt().toInstant()
                + "|"
                + txn.direction()
                + "|"
                + txn.amount()
                + "|"
                + normalize(txn.merchant())
                + "|"
                + txn.category();
    }

    private static String describe(NormalizedTxn txn) {
        return "account=" + txn.accountLast4()
                + ", occurredAt=" + txn.occurredAt()
                + ", direction=" + txn.direction()
                + ", amount=" + txn.amount()
                + ", category=" + txn.category()
                + ", merchant=" + txn.merchant()
                + ", sourceMessageIds=" + txn.sourceMessageIds();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                    .replaceAll("\\s+", " ")
                    .toUpperCase();
    }

    private static boolean safeEquals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    public record Divergence(
            String what,
            String inSql,
            String inDocuments) {
    }
}