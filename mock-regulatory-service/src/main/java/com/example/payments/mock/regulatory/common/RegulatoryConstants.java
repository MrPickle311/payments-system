package com.example.payments.mock.regulatory.common;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class RegulatoryConstants {

  public static final String REGULATORY_PATH = "/api/v1/regulatory";
  public static final String REPORT_PATH = "/report";
  public static final String DUPLICATE_RESPONSE = "Duplicate - Already Processed";
  public static final String CHAOS_RESPONSE = "Chaos Mode: Random Failure";
  public static final String ACCEPTED_RESPONSE = "Accepted";
}
