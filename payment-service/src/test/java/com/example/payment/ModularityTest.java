package com.example.payment;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.example.payments",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ModularityTest {

  @ArchTest
  static final ArchRule MODULARITY_RULE = slices().matching("com.example.payments.(*)..").should()
      .beFreeOfCycles().ignoreDependency(alwaysTrue(),
          resideInAnyPackage("..api.model..", "..api.generated..", "..sharedkernel.."));
}
