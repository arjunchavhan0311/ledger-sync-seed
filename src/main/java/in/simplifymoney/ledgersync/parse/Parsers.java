package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.RawMessage;

import java.util.List;
import java.util.Optional;

/**
 * Selects the parser responsible for a raw message.
 */
public final class Parsers {

    private final List<MessageParser> parsers;

    /**
     * Default parser registry.
     *
     * Keep the parser order deterministic.
     */
    public Parsers() {
        this(List.of(
                new HdfcSmsParser(),
                new IciciSmsParser(),
                new EmailParser()
        ));
    }

    /**
     * Constructor useful for tests and custom parser registration.
     */
    public Parsers(List<MessageParser> parsers) {
        if (parsers == null) {
            throw new IllegalArgumentException("parsers must not be null");
        }

        this.parsers = List.copyOf(parsers);
    }

    /**
     * Find the first parser that supports the message and parse it.
     *
     * An unsupported message is a normal non-transaction outcome.
     */
    public Optional<ParsedTxn> parse(RawMessage m) {

        if (m == null) {
            return Optional.empty();
        }

        for (MessageParser parser : parsers) {

            if (parser.supports(m)) {
                return parser.parse(m);
            }
        }

        return Optional.empty();
    }
}