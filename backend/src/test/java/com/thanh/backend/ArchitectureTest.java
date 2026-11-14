package com.thanh.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Executable form of the module and layer rules in AGENTS.md. The module graph below is the
 * dependency set that exists today, including known cycles (booking/payment, flight/reference
 * data). It prevents new edges; removing an edge is always allowed and the map may then be shrunk.
 */
class ArchitectureTest {
    private static final String ROOT = "com.thanh.backend";

    /** module -> modules it may depend on (besides itself). */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "common", Set.of(),
            "auth", Set.of("common"),
            "airport", Set.of("common", "flight"),
            "airline", Set.of("common", "flight"),
            "aircraft", Set.of("common", "flight", "airline"),
            "flight", Set.of("common", "auth", "airport", "airline", "aircraft"),
            "booking", Set.of("common", "auth", "flight", "airport", "airline", "payment"),
            "payment", Set.of("common", "auth", "flight", "booking"),
            "notification", Set.of("common", "auth", "booking")
    );

    private static JavaClasses classes;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    static Stream<String> modules() {
        return ALLOWED.keySet().stream().sorted();
    }

    @Test
    void everyProductionModuleIsCoveredByTheGraph() {
        Set<String> found = classes.stream()
                .map(c -> c.getPackageName())
                .filter(p -> p.startsWith(ROOT + "."))
                .map(p -> p.substring(ROOT.length() + 1).split("\\.")[0])
                .collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(ALLOWED.keySet()).containsAll(found);
    }

    @ParameterizedTest
    @MethodSource("modules")
    void moduleDependsOnlyOnItsAllowedModules(String module) {
        String[] forbidden = ALLOWED.keySet().stream()
                .filter(other -> !other.equals(module) && !ALLOWED.get(module).contains(other))
                .map(other -> ROOT + "." + other + "..")
                .toArray(String[]::new);
        noClasses().that().resideInAPackage(ROOT + "." + module + "..")
                .should().dependOnClassesThat().resideInAnyPackage(forbidden)
                .because("module '" + module + "' may only depend on " + ALLOWED.get(module))
                .check(classes);
    }

    @Test
    void controllersDoNotAccessRepositoriesDirectly() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("controllers handle transport; services own business decisions")
                .check(classes);
    }

    @Test
    void serviceRepositoryAndEntityLayersDoNotDependOnControllers() {
        noClasses().that().resideInAnyPackage("..service..", "..repository..", "..entity..", "..mapper..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .check(classes);
    }

    @Test
    void entitiesDoNotDependOnServicesOrDtos() {
        noClasses().that().resideInAPackage("..entity..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..dto..", "..controller..")
                .check(classes);
    }

    @Test
    void controllerEndpointsNeverTakeOrReturnEntities() {
        ArchCondition<JavaMethod> noEntityTypes = new ArchCondition<>("not use persistence entities as parameter or return types") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Stream.concat(method.getReturnType().getAllInvolvedRawTypes().stream(),
                                method.getParameterTypes().stream().flatMap(t -> t.getAllInvolvedRawTypes().stream()))
                        .filter(type -> type.getPackageName().contains(".entity"))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " uses entity " + type.getName())));
            }
        };
        methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..").and().arePublic()
                .should(noEntityTypes)
                .because("controllers exchange DTOs; reading the principal's id inside a method body is fine")
                .check(classes);
    }
}
