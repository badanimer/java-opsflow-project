package com.opsflow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * A rule checked by a test (T-54; see Rules Checked By Tests and Layout in
 * docs/backend/backend-design.md): the dependencies between the packages.
 *
 * <ul>
 * <li>The shared code in {@code common} depends on no module.</li>
 * <li>A controller does not read the database itself: it calls a service.</li>
 * <li>A module does not use the repository of another module: it calls the service.</li>
 * </ul>
 *
 * The rules read the compiled classes of {@code src/main}, so a new module is covered without a change
 * here. Each rule is also run over the made-up application in the package {@code rulefixture}, which
 * breaks each rule once: a rule that could not fail would prove nothing.
 */
class PackageDependencyRulesTest {

	private static final String APPLICATION = "com.opsflow";
	private static final String FIXTURE = "rulefixture";

	private static final JavaClasses APPLICATION_CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
			.importPackages(APPLICATION);

	private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURE);

	// The rules, for an application whose root package is given.

	static ArchRule commonDependsOnNoModule(String root) {
		DescribedPredicate<JavaClass> ofAModule = DescribedPredicate.describe("of a module",
				type -> type.getPackageName().startsWith(root + ".") && !moduleOf(root, type).equals("common"));
		return noClasses().that().resideInAPackage(root + ".common..")
				.should().dependOnClassesThat(ofAModule)
				.because("every module uses the shared code, so the shared code must not need a module");
	}

	static ArchRule controllersDoNotReadTheDatabase(String root) {
		return noClasses().that().resideInAPackage(root + "..").and().areAnnotatedWith(RestController.class)
				.should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..", "javax.sql..")
				.because("a controller calls a service, and the service owns the transaction");
	}

	static ArchRule repositoriesAreUsedOnlyByTheirModule(String root) {
		DescribedPredicate<JavaClass> repositories = DescribedPredicate.describe("repositories",
				type -> type.getSimpleName().endsWith("Repository") || type.isAnnotatedWith(Repository.class));
		ArchCondition<JavaClass> beUsedOnlyByTheirModule = new ArchCondition<>("be used only by their own module") {
			@Override
			public void check(JavaClass repository, ConditionEvents events) {
				for (Dependency use : repository.getDirectDependenciesToSelf()) {
					JavaClass user = use.getOriginClass();
					if (user.getPackageName().startsWith(root + ".") && !moduleOf(root, user).equals(moduleOf(root, repository))) {
						events.add(SimpleConditionEvent.violated(use, use.getDescription()));
					}
				}
			}
		};
		return classes().that().resideInAPackage(root + "..").and(repositories)
				.should(beUsedOnlyByTheirModule)
				.because("another module calls the service, which keeps the rules of the data");
	}

	/** The module of a class: the package directly under the root, such as {@code auth} or {@code common}. */
	private static String moduleOf(String root, JavaClass type) {
		String below = type.getPackageName().substring(root.length() + 1);
		int dot = below.indexOf('.');
		return dot < 0 ? below : below.substring(0, dot);
	}

	// The application keeps the rules.

	@Test
	void theSharedCodeDependsOnNoModule() {
		commonDependsOnNoModule(APPLICATION).check(APPLICATION_CLASSES);
	}

	@Test
	void noControllerReadsTheDatabaseItself() {
		controllersDoNotReadTheDatabase(APPLICATION).check(APPLICATION_CLASSES);
	}

	@Test
	void noModuleUsesTheRepositoryOfAnotherModule() {
		repositoriesAreUsedOnlyByTheirModule(APPLICATION).check(APPLICATION_CLASSES);
	}

	/** The rules would pass for the wrong reason if the classes were not found, or if the tests were among them. */
	@Test
	void theRulesReadTheClassesOfTheApplicationAndNotThoseOfTheTests() {
		List<String> names = APPLICATION_CLASSES.stream().map(JavaClass::getName).toList();

		assertThat(names).contains("com.opsflow.auth.AuthController", "com.opsflow.auth.AuthRepository",
				"com.opsflow.common.security.CurrentUser");
		assertThat(names).doesNotContain("com.opsflow.auth.PermissionProbeController", getClass().getName());
	}

	// Each rule fails for the mistake it is there for, and only for it.

	@Test
	void sharedCodeThatUsesAModuleIsFound() {
		assertThat(brokenBy(commonDependsOnNoModule(FIXTURE))).containsExactly("rulefixture.common.SharedShortcut");
	}

	@Test
	void aControllerThatReadsTheDatabaseIsFound() {
		assertThat(brokenBy(controllersDoNotReadTheDatabase(FIXTURE))).containsExactly("rulefixture.orders.OrderController");
	}

	@Test
	void aModuleThatUsesTheRepositoryOfAnotherModuleIsFound() {
		assertThat(brokenBy(repositoriesAreUsedOnlyByTheirModule(FIXTURE))).containsExactly("rulefixture.tickets.TicketShortcut");
	}

	/** The classes of the made-up application that the rule names in its failures. */
	private static List<String> brokenBy(ArchRule rule) {
		List<String> details = rule.evaluate(FIXTURE_CLASSES).getFailureReport().getDetails();
		return FIXTURE_CLASSES.stream().map(JavaClass::getName)
				.filter(name -> details.stream().anyMatch(detail -> startsAt(detail, name)))
				.sorted().toList();
	}

	/** Whether the failure is about something in this class: ArchUnit writes the origin of a dependency first. */
	private static boolean startsAt(String detail, String className) {
		int origin = detail.indexOf('<');
		return origin >= 0 && detail.startsWith(className, origin + 1);
	}

}
