package br.com.controlegastos;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    @Test
    void moduleDependenciesRespectDeclaredBoundaries() {
        ApplicationModules.of(ControleGastosApplication.class).verify();
    }
}
