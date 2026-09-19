package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Converts raw bank messages into the normalized ledger.
 *
 * A message is evidence of a transaction, not necessarily a transaction
 * itself. One real transaction may therefore have several source messages
 * (for example an SMS and an email, or a duplicate upload).
 *
 * Responsibilities:
 *
 * 1. Read the JSONL corpus.
 * 2. Parse supported messages.
 * 3. Ignore messages that are not transactions.
 * 4. Deduplicate transaction evidence.
 * 5. Identify paired own-account transfers.
 * 6. Classify MICRO / TRANSFER / SPEND / INCOME.
 * 7. Avoid inserting an already-existing transaction.
 * 8. Preserve every source message ID.
 */
public final class IngestService {

    /**
     * Assignment rule:
     *
     * MICRO = UPI debit of Rs.100 or less.
     */
    private static final BigDecimal MICRO_LIMIT =
            new BigDecimal("100.00");

    /**
     * Transfer messages for the two accounts can be a few minutes apart.
     */
    private static final Duration TRANSFER_WINDOW =
            Duration.ofMinutes(5);

    private final Parsers parsers;
    private final LedgerStore store;

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    /**
     * Reads, parses, deduplicates, classifies and stores one corpus.
     *
     * The operation is idempotent with respect to transactions that are
     * already present in the supplied LedgerStore.
     */
    public Stats ingestFile(Path corpus) throws IOException {

        List<RawMessage> messages = readCorpus(corpus);

        int skipped = 0;
        List<ParsedTxn> parsed = new ArrayList<>();

        /*
         * -------------------------------------------------------------
         * 1. Parse all messages first.
         * -------------------------------------------------------------
         *
         * Transfer detection cannot be done safely while processing one
         * message at a time because its matching leg may appear later.
         */
        for (RawMessage message : messages) {

            Optional<ParsedTxn> result = parsers.parse(message);

            if (result.isEmpty()) {
                skipped++;
            } else {
                parsed.add(result.get());
            }
        }

        /*
         * -------------------------------------------------------------
         * 2. Deduplicate evidence.
         * -------------------------------------------------------------
         */
        List<TransactionGroup> groups = deduplicate(parsed);

        /*
         * -------------------------------------------------------------
         * 3. Find own-account transfer pairs.
         * -------------------------------------------------------------
         *
         * A transfer is still represented by TWO ledger transactions:
         *
         *     account A DEBIT
         *     account B CREDIT
         *
         * Both receive category TRANSFER.
         */
        Set<Integer> transferIndexes =
                findTransferIndexes(groups);

        /*
         * Existing transaction identities are loaded once so that:
         *
         *     ingest(corpus)
         *     ingest(corpus)
         *
         * does not create another copy of the ledger.
         */
        Set<TransactionKey> existing =
                existingTransactionKeys();

        int written = 0;

        /*
         * -------------------------------------------------------------
         * 4. Convert groups into NormalizedTxn.
         * -------------------------------------------------------------
         */
        for (int i = 0; i < groups.size(); i++) {

            TransactionGroup group = groups.get(i);

            ParsedTxn txn = group.transaction();

            Category category;

            if (transferIndexes.contains(i)) {

                category = Category.TRANSFER;

            } else if (isMicro(txn)) {

                category = Category.MICRO;

            } else if (txn.direction() == Direction.DEBIT) {

                category = Category.SPEND;

            } else {

                category = Category.INCOME;
            }

            NormalizedTxn normalized = new NormalizedTxn(
                    txn.accountLast4(),
                    txn.occurredAt(),
                    txn.direction(),
                    money(txn.amount()),
                    category,
                    txn.merchant(),
                    group.sourceMessageIds()
            );

            TransactionKey key =
                    TransactionKey.from(normalized);

            /*
             * Idempotency:
             *
             * If the same transaction already exists in the store,
             * don't insert it again.
             */
            if (existing.contains(key)) {
                continue;
            }

            store.save(normalized);
            existing.add(key);
            written++;
        }

        return new Stats(
                messages.size(),
                written,
                skipped
        );
    }

    /**
     * Reads the JSONL corpus.
     */
    public static List<RawMessage> readCorpus(Path corpus)
            throws IOException {

        List<RawMessage> out = new ArrayList<>();

        try (Stream<String> lines = Files.lines(corpus)) {

            for (String line :
                    (Iterable<String>) lines
                            .filter(s -> !s.isBlank())
                            ::iterator) {

                Map<String, Object> object =
                        Json.parseObject(line);

                out.add(
                        new RawMessage(
                                (String) object.get("message_id"),
                                (String) object.get("channel"),
                                (String) object.get("sender"),
                                OffsetDateTime.parse(
                                        (String) object.get("received_at")
                                ),
                                (String) object.get("device_id"),
                                (String) object.get("body")
                        )
                );
            }
        }

        return out;
    }

    /**
     * Deduplicates message evidence belonging to the same transaction.
     *
     * We deliberately do NOT use message_id as the identity because the
     * assignment states that the same underlying message can be uploaded
     * more than once with a different message_id.
     *
     * The transaction identity is:
     *
     *     account
     *     occurred_at
     *     direction
     *     amount
     *     normalized merchant
     *
     * Email parsers should use the transaction date from the email itself,
     * not the time at which the phone received the email.
     */
    private List<TransactionGroup> deduplicate(
            List<ParsedTxn> parsed) {

        Map<DedupKey, TransactionGroup> groups =
                new LinkedHashMap<>();

        for (ParsedTxn txn : parsed) {

            if (txn.accountLast4() == null
                    || txn.occurredAt() == null
                    || txn.direction() == null
                    || txn.amount() == null) {
                continue;
            }

           DedupKey key = new DedupKey(
        txn.accountLast4(),
        txn.occurredAt().toInstant(),
        txn.direction(),
        money(txn.amount()),
        normalizeMerchant(txn.merchant())
);

            TransactionGroup existing =
                    groups.get(key);

            if (existing == null) {

                TransactionGroup group =
                        new TransactionGroup(txn);

                group.addSourceMessageId(
                        txn.sourceMessageId()
                );

                groups.put(key, group);

            } else {

                existing.addSourceMessageId(
                        txn.sourceMessageId()
                );
            }
        }

        List<TransactionGroup> result =
                new ArrayList<>(groups.values());

        result.sort(
                Comparator
                        .comparing(
                                (TransactionGroup g) ->
                                        g.transaction().occurredAt()
                        )
                        .thenComparing(
                                g -> g.transaction().accountLast4()
                        )
                        .thenComparing(
                                g -> g.transaction().direction()
                        )
                        .thenComparing(
                                g -> g.transaction().amount()
                        )
                        .thenComparing(
                                g -> normalizeMerchant(
                                        g.transaction().merchant()
                                )
                        )
        );

        return result;
    }

    /**
     * Finds transfer pairs.
     *
     * We match one debit with one credit:
     *
     *     different account
     *     opposite direction
     *     same amount
     *     same transfer counterparty
     *     close occurrence time
     *
     * A transaction is matched at most once.
     */
    private Set<Integer> findTransferIndexes(
            List<TransactionGroup> groups) {

        Set<Integer> transferIndexes =
                new HashSet<>();

        Set<Integer> alreadyMatched =
                new HashSet<>();

        for (int i = 0; i < groups.size(); i++) {

            if (alreadyMatched.contains(i)) {
                continue;
            }

            ParsedTxn left =
                    groups.get(i).transaction();

            if (!isTransferLike(left)) {
                continue;
            }

            int bestIndex = -1;
            Duration bestDifference = null;

            for (int j = i + 1; j < groups.size(); j++) {

                if (alreadyMatched.contains(j)) {
                    continue;
                }

                ParsedTxn right =
                        groups.get(j).transaction();

                if (!isTransferLike(right)) {
                    continue;
                }

                if (!isTransferPair(left, right)) {
                    continue;
                }

                Duration difference =
                        timeDifference(
                                left.occurredAt(),
                                right.occurredAt()
                        );

                if (bestDifference == null
                        || difference.compareTo(
                                bestDifference
                        ) < 0) {

                    bestIndex = j;
                    bestDifference = difference;
                }
            }

            if (bestIndex >= 0) {

                transferIndexes.add(i);
                transferIndexes.add(bestIndex);

                alreadyMatched.add(i);
                alreadyMatched.add(bestIndex);
            }
        }

        return transferIndexes;
    }

    /**
     * Determines whether two transactions represent opposite legs of
     * the same own-account transfer.
     */
    private boolean isTransferPair(
            ParsedTxn left,
            ParsedTxn right) {

        /*
         * The money must move between different accounts.
         */
        if (left.accountLast4().equals(
                right.accountLast4())) {

            return false;
        }

        /*
         * One account must lose money and the other must receive it.
         */
        if (left.direction() == right.direction()) {
            return false;
        }

        /*
         * Both legs must have the same amount.
         */
        if (money(left.amount()).compareTo(
                money(right.amount())) != 0) {

            return false;
        }

        /*
         * They should refer to the same transfer counterparty.
         */
        if (!sameTransferCounterparty(
                left.merchant(),
                right.merchant())) {

            return false;
        }

        Duration difference =
                timeDifference(
                        left.occurredAt(),
                        right.occurredAt()
                );

        return difference.compareTo(
                TRANSFER_WINDOW
        ) <= 0;
    }

    /**
     * Only transfer-like transaction descriptions are eligible for
     * transfer pairing.
     *
     * We deliberately do not classify every debit/credit as a transfer.
     */
    private boolean isTransferLike(
            ParsedTxn txn) {

        String merchant =
                normalizeMerchant(txn.merchant());

        /*
         * IMPS/P2A/... is the explicit own-account transfer format
         * used by the corpus.
         */
        if (merchant.contains("IMPS/P2A/")) {
            return true;
        }

        /*
         * "NEFT INWARD SELF" identifies an own-account transfer
         * description, but it still needs a matching opposite leg
         * before this service labels it TRANSFER.
         */
        return merchant.contains("NEFT INWARD SELF");
    }

    private boolean sameTransferCounterparty(
            String left,
            String right) {

        return normalizeMerchant(left)
                .equals(
                        normalizeMerchant(right)
                );
    }

    /**
     * MICRO = UPI debit <= Rs.100.
     *
     * Both forms occurring in the corpus are supported:
     *
     *     UPI/...
     *     UPI ...
     *
     * The second form is important for messages such as:
     *
     *     UPI MANDATE VERIFY
     */
    private boolean isMicro(
            ParsedTxn txn) {

        if (txn.direction() != Direction.DEBIT) {
            return false;
        }

        if (txn.amount() == null) {
            return false;
        }

        if (money(txn.amount()).compareTo(
                MICRO_LIMIT
        ) > 0) {

            return false;
        }

        String merchant =
                normalizeMerchant(txn.merchant());

        return merchant.startsWith("UPI/")
                || merchant.startsWith("UPI ");
    }

    /**
     * Builds the identities already present in the LedgerStore.
     *
     * LedgerStore intentionally does not promise uniqueness, so the
     * ingestion layer must protect repeated processing.
     */
    private Set<TransactionKey> existingTransactionKeys() {

        Set<TransactionKey> result =
                new HashSet<>();

        for (NormalizedTxn txn : store.all()) {
            result.add(
                    TransactionKey.from(txn)
            );
        }

        return result;
    }

    /**
     * Normalizes money to exactly two decimal places.
     */
    private static BigDecimal money(
            BigDecimal amount) {

        return amount.setScale(2);
    }

    /**
     * Normalizes merchant text only for comparison.
     *
     * The original merchant value is retained in NormalizedTxn.
     */
    private static String normalizeMerchant(
            String merchant) {

        if (merchant == null) {
            return "";
        }

        return merchant
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }

    private static Duration timeDifference(
            OffsetDateTime left,
            OffsetDateTime right) {

        return Duration.between(
                left,
                right
        ).abs();
    }

    /**
     * Identity of a normalized transaction.
     *
     * source_message_ids are deliberately NOT included because two
     * messages can evidence the same transaction.
     */
   private record TransactionKey(
        String accountLast4,
        Instant occurredAt,
        Direction direction,
        BigDecimal amount,
        String merchant,
        Category category) {

    static TransactionKey from(
            NormalizedTxn txn) {

        return new TransactionKey(
                txn.accountLast4(),
                txn.occurredAt().toInstant(),
                txn.direction(),
                money(txn.amount()),
                normalizeMerchant(txn.merchant()),
                txn.category()
        );
    }
}

    /**
     * Key used while grouping raw parsed evidence.
     */
    private record DedupKey(
        String accountLast4,
        Instant occurredAt,
        Direction direction,
        BigDecimal amount,
        String merchant) {
}

    /**
     * One real transaction and all messages that evidence it.
     */
    private static final class TransactionGroup {

        private final ParsedTxn transaction;
        private final Set<String> sourceMessageIds =
                new TreeSet<>();

        private TransactionGroup(
                ParsedTxn transaction) {

            this.transaction = transaction;
        }

        private ParsedTxn transaction() {
            return transaction;
        }

        private void addSourceMessageId(
                String messageId) {

            if (messageId != null
                    && !messageId.isBlank()) {

                sourceMessageIds.add(messageId);
            }
        }

        private List<String> sourceMessageIds() {
            return List.copyOf(sourceMessageIds);
        }
    }

    public record Stats(
            int messagesRead,
            int transactionsWritten,
            int messagesSkipped) {
    }
}