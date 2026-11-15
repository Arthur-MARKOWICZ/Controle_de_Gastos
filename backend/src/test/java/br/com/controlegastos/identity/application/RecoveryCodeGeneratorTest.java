package br.com.controlegastos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecoveryCodeGeneratorTest {

    private final RecoveryCodeGenerator generator = new RecoveryCodeGenerator();

    @Test
    @DisplayName("gera dez códigos no formato XXXXX-XXXXX")
    void geraDezCodigosNoFormato() {
        List<String> codes = generator.generate();

        assertThat(codes).hasSize(10).allMatch(code -> code.matches("[2-9A-HJKMNP-Z]{5}-[2-9A-HJKMNP-Z]{5}"));
    }

    @Test
    @DisplayName("não usa caracteres ambíguos 0, 1, I, L e O")
    void naoUsaCaracteresAmbiguos() {
        for (int i = 0; i < 50; i++) {
            assertThat(String.join("", generator.generate())).doesNotContainPattern("[01ILO]");
        }
    }

    @Test
    @DisplayName("os códigos de um lote são distintos entre si")
    void codigosDoLoteSaoDistintos() {
        assertThat(new HashSet<>(generator.generate())).hasSize(10);
    }

    @Test
    @DisplayName("lotes consecutivos não se repetem")
    void lotesNaoSeRepetem() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.addAll(generator.generate());
        }
        assertThat(seen).hasSize(1000);
    }

    @Test
    @DisplayName("códigos estão em maiúsculas para casar com a normalização do login")
    void codigosEmMaiusculas() {
        generator.generate().forEach(code -> assertThat(code).isEqualTo(code.toUpperCase()));
    }

    @Test
    @DisplayName("cada código tem exatamente um separador")
    void umSeparadorPorCodigo() {
        generator.generate().forEach(code -> assertThat(code.chars().filter(c -> c == '-').count()).isEqualTo(1));
    }

    @Test
    @DisplayName("cada código tem 11 caracteres")
    void onzeCaracteres() {
        generator.generate().forEach(code -> assertThat(code).hasSize(11));
    }
}
