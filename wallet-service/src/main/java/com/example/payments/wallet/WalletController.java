package com.example.payments.wallet;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import com.example.payments.wallet.application.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService walletService;

    @PostMapping("/debit")
    public DebitResponse debit(@RequestBody DebitRequest request) {
        return walletService.debit(request);
    }
}
