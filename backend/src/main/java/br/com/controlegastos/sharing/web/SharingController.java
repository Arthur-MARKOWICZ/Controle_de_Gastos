package br.com.controlegastos.sharing.web;

import br.com.controlegastos.sharing.application.SharingService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/envelopes/{envelopeId}/participants")
class SharingController {

    private final SharingService sharing;

    SharingController(SharingService sharing) {
        this.sharing = sharing;
    }

    @GetMapping
    ParticipantListResponse list(@PathVariable UUID envelopeId) {
        List<ParticipantResponse> items = sharing.list(envelopeId).stream()
                .map(ParticipantResponse::from)
                .toList();
        return new ParticipantListResponse(items);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ParticipantResponse invite(@PathVariable UUID envelopeId, @RequestBody JsonNode body) {
        return ParticipantResponse.from(sharing.invite(envelopeId, emailFrom(body)));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID envelopeId, @PathVariable UUID userId) {
        sharing.remove(envelopeId, userId);
    }

    private static String emailFrom(JsonNode body) {
        if (body == null || !body.isObject() || !body.has("email") || !body.get("email").isString()) {
            throw new IllegalArgumentException("Informe o e-mail do convidado");
        }
        return body.get("email").asString();
    }

    record ParticipantListResponse(List<ParticipantResponse> items) {
    }

    record ParticipantResponse(UUID userId, String email, Instant addedAt) {
        static ParticipantResponse from(SharingService.ParticipantView view) {
            return new ParticipantResponse(view.userId(), view.email(), view.addedAt());
        }
    }
}
