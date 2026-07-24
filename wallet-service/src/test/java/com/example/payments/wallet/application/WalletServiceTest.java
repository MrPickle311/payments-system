package com.example.payments.wallet.application;

import static com.example.payments.wallet.common.WalletConstants.STATUS_INSUFFICIENT_FUNDS;
import static com.example.payments.wallet.common.WalletConstants.STATUS_SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.payments.wallet.application.port.WalletAccountPort;
import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.grpc.DebitRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletAccountPort walletAccountPort;

    @InjectMocks
    private WalletService walletService;

    private static final String USD = "USD";
    private static final BigDecimal BALANCE = new BigDecimal("1000.00");

    private WalletAccount createAccount(long userId, String currency) {
        return WalletAccount.builder()
                .id(userId)
                .userId(userId)
                .balance(BALANCE)
                .currency(currency)
                .build();
    }

    @Test
    void testDebitBetweenUsersCreateAccount() {
        DebitRequest req = DebitRequest.newBuilder()
                .setPaymentId(10L)
                .setAmount("100.00")
                .setCurrency(USD)
                .setSourceUserId(1L)
                .setTargetUserId(2L)
                .build();
        when(walletAccountPort.findByUserIdAndCurrency(1L, USD)).thenReturn(Optional.empty());
        when(walletAccountPort.findByUserIdAndCurrency(2L, USD)).thenReturn(Optional.empty());
        WalletAccount src = createAccount(1L, USD);
        WalletAccount tgt = createAccount(2L, USD);
        when(walletAccountPort.save(any(WalletAccount.class))).thenReturn(src, tgt);

        String status = walletService.debitBetweenUsers(req);

        assertEquals(STATUS_SUCCESS, status);
        assertEquals(new BigDecimal("900.00"), src.getBalance());
    }

    @Test
    void testDebitBetweenUsersInsufficientFunds() {
        DebitRequest req = DebitRequest.newBuilder()
                .setPaymentId(11L)
                .setAmount("2000.00")
                .setCurrency(USD)
                .setSourceUserId(1L)
                .setTargetUserId(2L)
                .build();
        WalletAccount src = createAccount(1L, USD);
        when(walletAccountPort.findByUserIdAndCurrency(1L, USD)).thenReturn(Optional.of(src));

        String status = walletService.debitBetweenUsers(req);

        assertEquals(STATUS_INSUFFICIENT_FUNDS, status);
        assertEquals(BALANCE, src.getBalance());
    }

    @Test
    void testDebitBetweenUsersSuccess() {
        DebitRequest req = DebitRequest.newBuilder()
                .setPaymentId(12L)
                .setAmount("200.00")
                .setCurrency(USD)
                .setSourceUserId(1L)
                .setTargetUserId(2L)
                .build();
        WalletAccount src = createAccount(1L, USD);
        WalletAccount tgt = createAccount(2L, USD);
        when(walletAccountPort.findByUserIdAndCurrency(1L, USD)).thenReturn(Optional.of(src));
        when(walletAccountPort.findByUserIdAndCurrency(2L, USD)).thenReturn(Optional.of(tgt));

        String status = walletService.debitBetweenUsers(req);

        assertEquals(STATUS_SUCCESS, status);
        assertEquals(new BigDecimal("800.00"), src.getBalance());
        assertEquals(new BigDecimal("1200.00"), tgt.getBalance());
    }
}
