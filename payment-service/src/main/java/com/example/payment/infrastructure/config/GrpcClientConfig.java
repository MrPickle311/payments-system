package com.example.payment.infrastructure.config;

import com.example.payments.authorization.grpc.AuthorizationServiceGrpc;
import com.example.payments.fee.grpc.FeeServiceGrpc;
import com.example.payments.fraud.grpc.FraudServiceGrpc;
import com.example.payments.fx.grpc.FxServiceGrpc;
import com.example.payments.limits.grpc.LimitsServiceGrpc;
import com.example.payments.sanctions.grpc.SanctionsServiceGrpc;
import com.example.payments.wallet.grpc.WalletServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean
    public FraudServiceGrpc.FraudServiceBlockingStub fraudCheckService(
            @GrpcClient("fraud-service") FraudServiceGrpc.FraudServiceBlockingStub stub) {
        return stub;
    }

    @Bean
    public AuthorizationServiceGrpc.AuthorizationServiceBlockingStub authorizationService(
            @GrpcClient("authorization-service") AuthorizationServiceGrpc.AuthorizationServiceBlockingStub stub) {
        return stub;
    }

    @Bean
    public LimitsServiceGrpc.LimitsServiceBlockingStub limitsService(
            @GrpcClient("limits-service") LimitsServiceGrpc.LimitsServiceBlockingStub stub) {
        return stub;
    }

    @Bean
    public SanctionsServiceGrpc.SanctionsServiceBlockingStub sanctionsService(
            @GrpcClient("sanctions-service") SanctionsServiceGrpc.SanctionsServiceBlockingStub stub) {
        return stub;
    }

    @Bean
    public FxServiceGrpc.FxServiceBlockingStub fxService(
            @GrpcClient("fx-service") FxServiceGrpc.FxServiceBlockingStub stub) {
        return stub;
    }

    @Bean
    public FeeServiceGrpc.FeeServiceBlockingStub feeService(
            @GrpcClient("fee-service") FeeServiceGrpc.FeeServiceBlockingStub stub) {
        return stub;
    }

    @Bean
    public WalletServiceGrpc.WalletServiceBlockingStub walletService(
            @GrpcClient("wallet-service") WalletServiceGrpc.WalletServiceBlockingStub stub) {
        return stub;
    }
}
