package in.simplifymoney.ledgersync;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.report.Reports;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class App {

    private static final Path DB =
            Path.of("data", "ledger");

    private static final Path MIGRATIONS =
            Path.of("db", "migration");

    private App() {}

    public static void main(String[] args) throws Exception {

        if (args == null || args.length == 0) {
            usage();
            System.exit(2);
            return;
        }

        Files.createDirectories(DB.getParent());

        switch (args[0].toLowerCase()) {

            case "migrate" -> migrate();

            case "ingest" -> {
                requireArguments(args, 2, "ingest <corpus.jsonl>");
                ingest(Path.of(args[1]));
            }

            case "report" -> {
                requireArguments(args, 2, "report <out-dir>");
                report(Path.of(args[1]));
            }

            default -> {
                System.err.println("unknown command: " + args[0]);
                usage();
                System.exit(2);
            }
        }
    }

    private static void migrate() {

        try (SqlLedgerStore store =
                     new SqlLedgerStore(DB)) {

            store.migrate(MIGRATIONS);

            System.out.println(
                    "ledger rows: " + store.count());
        }
    }

    private static void ingest(Path corpus) throws Exception {

        if (!Files.exists(corpus)) {
            throw new IllegalArgumentException(
                    "corpus does not exist: " + corpus);
        }

        if (!Files.isRegularFile(corpus)) {
            throw new IllegalArgumentException(
                    "corpus is not a file: " + corpus);
        }

        try (SqlLedgerStore store =
                     new SqlLedgerStore(DB)) {

            store.migrate(MIGRATIONS);

            IngestService service =
                    new IngestService(
                            new Parsers(),
                            store);

            IngestService.Stats stats =
                    service.ingestFile(corpus);

            System.out.println(stats);
            System.out.println(
                    "ledger rows: " + store.count());
        }
    }

    private static void report(Path outputDirectory)
            throws Exception {

        Files.createDirectories(outputDirectory);

        try (SqlLedgerStore store =
                     new SqlLedgerStore(DB)) {

            /*
             * SQL intentionally contains legacy duplicate rows.
             * The report must contain one entry per real transaction.
             */
            var ledger = deduplicateForReport(store.all());

            Files.writeString(
                    outputDirectory.resolve("ledger.json"),
                    Json.writePretty(
                            Reports.ledgerDocument(ledger)),
                    StandardCharsets.UTF_8);

            Files.writeString(
                    outputDirectory.resolve("summary.json"),
                    Json.writePretty(
                            Reports.summary(ledger)),
                    StandardCharsets.UTF_8);

            Files.writeString(
                    outputDirectory.resolve("reconciliation.json"),
                    Json.writePretty(
                            Reports.reconciliation(ledger)),
                    StandardCharsets.UTF_8);

            System.out.println(
                    "wrote 3 files to "
                            + outputDirectory);
        }
    }

    private static java.util.List<NormalizedTxn> deduplicateForReport(
            java.util.List<NormalizedTxn> rows) {

        Map<String, NormalizedTxn> unique =
                new LinkedHashMap<>();

        for (NormalizedTxn txn : rows) {

            String key =
                    txn.accountLast4()
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

            unique.putIfAbsent(key, txn);
        }

        return new java.util.ArrayList<>(unique.values());
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                        .replaceAll("\\s+", " ")
                        .toUpperCase();
    }

    private static void requireArguments(
            String[] args,
            int requiredCount,
            String usage) {

        if (args.length < requiredCount) {
            throw new IllegalArgumentException(
                    "usage: " + usage);
        }
    }

    private static void usage() {

        System.err.println(
                "usage:\n"
                        + "  migrate\n"
                        + "  ingest <corpus.jsonl>\n"
                        + "  report <out-dir>");
    }
}