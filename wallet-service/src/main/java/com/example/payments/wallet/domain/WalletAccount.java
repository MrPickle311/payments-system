package com.example.payments.wallet.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "wallet_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletAccount {
    @Id
    private Long id;
    private Long userId;
    private BigDecimal balance;
    private String currency;
}
