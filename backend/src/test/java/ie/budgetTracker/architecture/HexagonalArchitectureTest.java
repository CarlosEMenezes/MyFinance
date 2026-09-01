package ie.budgetTracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the hexagonal dependency rule of spec §4. These rules are written
 * before the code they govern and must never be weakened (spec §0.3).
 */
@AnalyzeClasses(packages = "ie.budgetTracker", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

	private static final String DOMAIN = "Domain";
	private static final String APPLICATION = "Application";
	private static final String INFRASTRUCTURE = "Infrastructure";
	private static final String API = "Api";

	@ArchTest
	static final ArchRule dependenciesPointInwardOnly = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.withOptionalLayers(true)
			.layer(DOMAIN).definedBy("ie.budgetTracker.domain..")
			.layer(APPLICATION).definedBy("ie.budgetTracker.application..")
			.layer(INFRASTRUCTURE).definedBy("ie.budgetTracker.infrastructure..")
			.layer(API).definedBy("ie.budgetTracker.api..")
			.whereLayer(API).mayNotBeAccessedByAnyLayer()
			.whereLayer(INFRASTRUCTURE).mayNotBeAccessedByAnyLayer()
			.whereLayer(APPLICATION).mayOnlyBeAccessedByLayers(API, INFRASTRUCTURE)
			.whereLayer(DOMAIN).mayOnlyBeAccessedByLayers(APPLICATION);

	@ArchTest
	static final ArchRule domainDependsOnNothingInThisApplication = noClasses()
			.that().resideInAPackage("ie.budgetTracker.domain..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"ie.budgetTracker.application..",
					"ie.budgetTracker.infrastructure..",
					"ie.budgetTracker.api..")
			.because("the domain is the innermost layer and depends on nothing (spec §4)");

	@ArchTest
	static final ArchRule domainCarriesNoSpringDependency = noClasses()
			.that().resideInAPackage("ie.budgetTracker.domain..")
			.should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
			.because("domain services are plain classes with pure methods (spec §4)");

	@ArchTest
	static final ArchRule domainCarriesNoPersistenceAnnotation = noClasses()
			.that().resideInAPackage("ie.budgetTracker.domain..")
			.should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
			.because("persistence is an infrastructure concern, never a domain one (spec §4)");

	@ArchTest
	static final ArchRule noFieldIsAFloatingPointNumber = noFields()
			.that().areDeclaredInClassesThat().resideInAPackage("ie.budgetTracker..")
			.should().haveRawType(double.class)
			.orShould().haveRawType(float.class)
			.orShould().haveRawType(Double.class)
			.orShould().haveRawType(Float.class)
			.because("every money value is a BigDecimal, scale 2, HALF_UP (spec §0.5)");
}
