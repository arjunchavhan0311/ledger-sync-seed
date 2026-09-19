package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.parse.Amounts;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class IncidentRegressionTest {

    @Test
    void waterCanUsesTransactionAmountNotAvailableBalance() {
        String body =
                "Rs.5 debited from a/c **4821 on 04-07-26 at 07:19 "
                + "to UPI/WATER CAN. Avl Bal: Rs.92,213.10.";

        assertEquals(
                new BigDecimal("5.00"),
                Amounts.first(body)
        );
    }
}