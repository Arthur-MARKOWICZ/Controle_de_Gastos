package br.com.controlegastos.privacy.web;

import br.com.controlegastos.privacy.application.TitularRightsService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/privacy")
public class TitularRightsController {

    private final TitularRightsService rights;

    TitularRightsController(TitularRightsService rights) {
        this.rights = rights;
    }

    @GetMapping("/export")
    Map<String, Object> export() {
        return rights.exportCurrentTitular();
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void erase(@RequestBody JsonNode body) {
        if (body == null || !body.has("password") || !body.get("password").isString()) {
            throw new IllegalArgumentException("A senha é obrigatória para excluir a conta");
        }
        rights.eraseCurrentTitular(body.get("password").asString());
    }
}
