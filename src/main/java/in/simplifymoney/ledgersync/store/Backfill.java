package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Backfill {

    private final SqlLedgerStore source;
    private final DocumentStore target;

    public Backfill(SqlLedgerStore source, DocumentStore target) {
        this.source = source;
        this.target = target;
    }

    public Result run() {
        List<NormalizedTxn> rows = source.all();

        long read = rows.size();
        long written = 0;
        long skipped = 0;

        Set<String> seen = new HashSet<>();

        for (NormalizedTxn txn : rows) {
            String key = transactionKey(txn);

            // SQL may contain historical duplicates.
            if (!seen.add(key)) {
                skipped++;
                continue;
            }

            // save() is idempotent in our document-store implementation.
            target.save(txn);
            written++;
        }

        return new Result(read, written, skipped);
    }

    private static String transactionKey(NormalizedTxn txn) {
        return txn.accountLast4()
                + "|"
                + txn.occurredAt().toInstant()
                + "|"
                + txn.direction()
                + "|"
                + txn.amount().setScale(2)
                + "|"
                + normalize(txn.merchant())
                + "|"
                + txn.category();
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    public record Result(long read, long written, long skipped) {}
}