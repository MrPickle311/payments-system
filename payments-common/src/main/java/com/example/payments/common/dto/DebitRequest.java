package com.example.payments.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitRequest {
  private Long paymentId;
  private BigDecimal amount;
  private String currency;
  private Long walletId;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ReserveFundsCommand {
    private Long paymentId;
    private Long walletId;
    private BigDecimal amount;
    private String type;
    private String currency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FundsReservedEvent {
    private Long paymentId;
    private Long walletId;
    private BigDecimal amount;
    private String type;
    private String currency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FundsReservationFailedEvent {
    private Long paymentId;
    private Long walletId;
    private String reason;
    private String currency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UnreserveFundsCommand {
    private Long paymentId;
    private Long walletId;
    private BigDecimal amount;
    private String type;
    private String currency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PostJournalEntryCommand {
    private Long paymentId;
    private Long payerWalletId;
    private Long payeeWalletId;
    private BigDecimal baseAmount;
    private BigDecimal feeAmount;
    private String currency;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class JournalEntryPostedEvent {
    private Long paymentId;
    private Long entryId;
  }
}
