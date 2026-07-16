package com.example.payments.fraud.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface PayerProfileRepository extends JpaRepository<PayerProfile, Long> {
}
