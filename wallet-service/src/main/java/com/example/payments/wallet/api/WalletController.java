package com.example.payments.wallet.api;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import com.example.payments.wallet.application.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.payments.wallet.common.WalletConstants.DEBIT_PATH;
import static com.example.payments.wallet.common.WalletConstants.WALLET_PATH;

@Slf4j
@RestController
@RequestMapping(WALLET_PATH)
@RequiredArgsConstructor
public class WalletController {
  private final WalletService walletService;

  @PostMapping(DEBIT_PATH)
  public DebitResponse debit(@RequestBody DebitRequest request) {
    return walletService.debit(request);
  }
}
