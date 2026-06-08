package com.example.payments.ledger;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.example.payments.ledger",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class LayeredArchitectureTest {
  private static final String API = "Api";
  private static final String APP = "Application";
  private static final String DOMAIN = "Domain";
  private static final String INFRA = "Infrastructure";

  @ArchTest
  static final ArchRule LAYER_DEPENDENCIES_ARE_RESPECTED =
      layeredArchitecture().consideringAllDependencies().layer(API).definedBy("..api..").layer(APP)
          .definedBy("..application..").layer(DOMAIN).definedBy("..domain..").layer(INFRA)
          .definedBy("..infrastructure..").whereLayer(API).mayNotBeAccessedByAnyLayer()
          .whereLayer(APP).mayOnlyBeAccessedByLayers(API, INFRA).whereLayer(DOMAIN)
          .mayOnlyBeAccessedByLayers(APP, INFRA, API).withOptionalLayers(true);
}
