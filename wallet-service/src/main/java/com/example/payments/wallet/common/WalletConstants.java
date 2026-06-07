package com.example.payments.wallet.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class WalletConstants {

  public static final String WALLET_PATH = "/wallets";
  public static final String DEBIT_PATH = "/debit";
  public static final String STATUS_INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";
  public static final String STATUS_SUCCESS = "SUCCESS";
  public static final String REF_PREFIX = "WLT-";
}
