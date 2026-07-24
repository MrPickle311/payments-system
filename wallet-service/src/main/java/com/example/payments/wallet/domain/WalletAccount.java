package com.example.payments.wallet.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletAccount {
    private Long id;
    private Long userId;
    private Long version;
    private BigDecimal balance;
    private String currency;
}
