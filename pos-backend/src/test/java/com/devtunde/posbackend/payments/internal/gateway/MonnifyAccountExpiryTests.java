package com.devtunde.posbackend.payments.internal.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.monnify.models.transaction.BankTransferResponse;

class MonnifyAccountExpiryTests {

    @Test
    @DisplayName("expiresOn wins when present")
    void expiresOnWinsWhenPresent() {
        BankTransferResponse bank = new BankTransferResponse();
        bank.setExpiresOn(LocalDateTime.of(2026, 11, 5, 19, 19, 32));
        bank.setRequestTime("2026-11-05T18:39:33");
        bank.setAccountDurationSeconds(2400);

        assertThat(MonnifyGateway.accountExpiryOf(bank)).isEqualTo(LocalDateTime.of(2026, 11, 5, 19, 19, 32));
    }

    @Test
    @DisplayName("without expiresOn the expiry is requestTime plus duration (documented shape)")
    void derivesExpiryFromRequestTimeAndDuration() {
        BankTransferResponse bank = new BankTransferResponse();
        bank.setRequestTime("2026-11-05T18:39:33");
        bank.setAccountDurationSeconds(2400);

        assertThat(MonnifyGateway.accountExpiryOf(bank)).isEqualTo(LocalDateTime.of(2026, 11, 5, 19, 19, 33));
    }

    @Test
    @DisplayName("neither signal present: expiry stays unknown (null)")
    void nullWhenNeitherSignalPresent() {
        BankTransferResponse bank = new BankTransferResponse();

        assertThat(MonnifyGateway.accountExpiryOf(bank)).isNull();
    }
}
