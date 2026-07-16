package com.example.payments.fraud.application;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "payer_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PayerProfile {
    @Id
    @jakarta.persistence.Column(name = "payer_id")
    private Long payerId;
    private String segment;

    @jakarta.persistence.Column(name = "kyc_status")
    private String kycStatus;
}
