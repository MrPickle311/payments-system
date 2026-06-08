package com.example.payments;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.example.payments",
    importOptions = {ImportOption.DoNotIncludeTests.class})
public class GlobalArchitectureTest {

  private static final String FEE_PACKAGE = "..fee..";
  private static final String FRAUD_PACKAGE = "..fraud..";
  private static final String PAYMENT_PACKAGE = "..payment..";

  @ArchTest
  static final ArchRule NO_SERVICE_IMPORTS_FROM_OTHER_SERVICES =
      slices().matching("com.example.payments.(*)..").should().notDependOnEachOther()
          .ignoreDependency(alwaysTrue(),
              JavaClass.Predicates.resideInAnyPackage("..common..", "..sharedkernel.."))
          .ignoreDependency(
              JavaClass.Predicates.resideInAnyPackage(FEE_PACKAGE, FRAUD_PACKAGE, PAYMENT_PACKAGE),
              JavaClass.Predicates.resideInAnyPackage(FEE_PACKAGE, FRAUD_PACKAGE, PAYMENT_PACKAGE));
}
