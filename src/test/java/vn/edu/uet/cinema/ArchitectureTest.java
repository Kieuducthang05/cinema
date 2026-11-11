package vn.edu.uet.cinema;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Enforces ADR 0001: api → business ← persistence, business depends on nothing but the JDK. */
@AnalyzeClasses(packages = "vn.edu.uet.cinema", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule layers = layeredArchitecture()
		.consideringOnlyDependenciesInLayers()
		.layer("Api").definedBy("..cinema.api..")
		.layer("Business").definedBy("..cinema.business..")
		.layer("Persistence").definedBy("..cinema.persistence..")
		.layer("Config").definedBy("..cinema.config..")
		.whereLayer("Api").mayOnlyBeAccessedByLayers("Config")
		.whereLayer("Persistence").mayOnlyBeAccessedByLayers("Config")
		.whereLayer("Business").mayOnlyBeAccessedByLayers("Api", "Persistence", "Config")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule businessIsFrameworkFree = classes()
		.that().resideInAPackage("..cinema.business..")
		.should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..cinema.business..")
		.allowEmptyShould(true);
}
