package br.com.controlegastos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDependency;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(ControleGastosApplication.class);

    @Test
    void moduleDependenciesRespectDeclaredBoundaries() {
        MODULES.verify();
    }

    /** ADR-0034: investimentos só conhece dinheiro e identidade; o orçamento não conhece investimentos. */
    @Test
    void investmentsIsIsolatedFromTheBudget() {
        ApplicationModule investments = MODULES.getModuleByName("investments").orElseThrow();

        assertThat(dependencyNames(investments)).isSubsetOf("identity", "shared.money");
        List<String> dependents = MODULES.stream()
                .filter(module -> dependencyNames(module).contains("investments"))
                .map(module -> module.getIdentifier().toString())
                .toList();
        assertThat(dependents).isEmpty();
    }

    private static Set<String> dependencyNames(ApplicationModule module) {
        return module.getDirectDependencies(MODULES).stream()
                .map(ApplicationModuleDependency::getTargetModule)
                .map(target -> target.getIdentifier().toString())
                .collect(Collectors.toSet());
    }
}
