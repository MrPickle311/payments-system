package com.example.payments.wallet.application;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import com.example.payments.wallet.domain.WalletAccount;
import com.example.payments.wallet.application.port.WalletAccountPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.example.payments.wallet.common.WalletConstants.STATUS_INSUFFICIENT_FUNDS;
import static com.example.payments.wallet.common.WalletConstants.STATUS_SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

  @Mock
  private WalletAccountPort walletAccountPort;

  @InjectMocks
  private WalletService walletService;

  private static final String CURRENCY_UNITED_STATES_DOLLAR = "USD";
  private static final String CURRENCY_EURO = "EUR";
  private static final BigDecimal BALANCE_ONE_THOUSAND = new BigDecimal("1000.00");

  private WalletAccount createAccount(String currency) {
    return WalletAccount.builder().id(1L).userId(1L).balance(BALANCE_ONE_THOUSAND)
        .currency(currency).build();
  }

  private DebitRequest createRequest(long paymentId, String amount, String currency) {
    return DebitRequest.builder().paymentId(paymentId).amount(new BigDecimal(amount))
        .currency(currency).build();
  }

  @Test
  void testDebitShouldCreateMockAccount() {
    DebitRequest request = createRequest(10L, "100.00", CURRENCY_UNITED_STATES_DOLLAR);
    when(walletAccountPort.findByUserIdAndCurrency(1L, CURRENCY_UNITED_STATES_DOLLAR))
        .thenReturn(Optional.empty());
    WalletAccount mockAccount = createAccount(CURRENCY_UNITED_STATES_DOLLAR);
    when(walletAccountPort.save(any(WalletAccount.class))).thenReturn(mockAccount);

    DebitResponse response = walletService.debit(request);

    assertEquals(STATUS_SUCCESS, response.getStatus());
    assertNotNull(response.getReferenceId());
    verify(walletAccountPort).findByUserIdAndCurrency(1L, CURRENCY_UNITED_STATES_DOLLAR);
    assertEquals(new BigDecimal("900.00"), mockAccount.getBalance());
  }

  @Test
  void testDebitInsufficientFunds() {
    DebitRequest request = createRequest(11L, "2000.00", CURRENCY_UNITED_STATES_DOLLAR);
    WalletAccount existingAccount = createAccount(CURRENCY_UNITED_STATES_DOLLAR);
    when(walletAccountPort.findByUserIdAndCurrency(1L, CURRENCY_UNITED_STATES_DOLLAR))
        .thenReturn(Optional.of(existingAccount));

    DebitResponse response = walletService.debit(request);

    assertEquals(STATUS_INSUFFICIENT_FUNDS, response.getStatus());
    assertEquals(BALANCE_ONE_THOUSAND, existingAccount.getBalance());
  }

  @Test
  void testDebitSuccess() {
    DebitRequest request = createRequest(12L, "200.00", CURRENCY_EURO);
    WalletAccount existingAccount = createAccount(CURRENCY_EURO);
    when(walletAccountPort.findByUserIdAndCurrency(1L, CURRENCY_EURO))
        .thenReturn(Optional.of(existingAccount));

    DebitResponse response = walletService.debit(request);

    assertEquals(STATUS_SUCCESS, response.getStatus());
    assertNotNull(response.getReferenceId());
    assertEquals(new BigDecimal("800.00"), existingAccount.getBalance());
    verify(walletAccountPort).save(existingAccount);
  }
}
