package com.example.payments.wallet;

import com.example.payments.common.dto.DebitRequest;
import com.example.payments.common.dto.DebitResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock Wallet Service (External Service).
 *
 * In a real production system, this would be a separate microservice.
 */
@Slf4j
@RestController
@RequestMapping("/wallets")
public class WalletController {

    @PostMapping("/debit")
    public DebitResponse debit(@RequestBody DebitRequest request) {
        log.info("[WalletService] Received debit request for paymentId={} amount={} {}",
                request.getPaymentId(), request.getAmount(), request.getCurrency());
        
        // Simulate wallet logic
        return DebitResponse.builder()
                .status("SUCCESS")
                .referenceId("WLT-" + System.currentTimeMillis())
                .build();
    }
}
