package br.com.controlegastos.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.controlegastos.identity.infrastructure.AuthAttemptRepository;
import br.com.controlegastos.support.FixedClockTestConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/** Contrato HTTP de participantes de verba (spec sharing/participants-api, ADR-0026). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(FixedClockTestConfiguration.class)
class ParticipantsApiIntegrationTest {

    private static final String PASSWORD = "frase segura de teste";
    private static final String PARTICIPANTS = "/api/v1/envelopes/{envelopeId}/participants";
    private static final String GENERIC_INVITE_FAILURE =
            "Não foi possível convidar este e-mail. Confira se a pessoa já tem conta verificada.";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.auth.cookie-secure", () -> false);
        registry.add("app.auth.cookie-name", () -> "refresh_token");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AuthAttemptRepository authAttempts;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetRateLimits() {
        authAttempts.deleteAll();
    }

    @Test
    @DisplayName("proprietário convida usuário verificado e o vê na listagem")
    void proprietarioConvidaEListaParticipante() throws Exception {
        String owner = verifiedToken("part-dono@example.com");
        verifiedToken("part-convidado@example.com");
        String envelopeId = createEnvelope(owner, "Mercado");

        invite(owner, envelopeId, "part-convidado@example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("part-convidado@example.com"))
                .andExpect(jsonPath("$.userId").value(userId("part-convidado@example.com").toString()));

        mockMvc.perform(get(PARTICIPANTS, envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].email").value("part-convidado@example.com"))
                .andExpect(jsonPath("$.items[0].addedAt").isNotEmpty());
    }

    @Test
    @DisplayName("convidar quem já participa não duplica o participante")
    void conviteRepetidoEIdempotente() throws Exception {
        String owner = verifiedToken("part-repetido-dono@example.com");
        verifiedToken("part-repetido@example.com");
        String envelopeId = createEnvelope(owner, "Mercado");
        invite(owner, envelopeId, "part-repetido@example.com").andExpect(status().isCreated());

        invite(owner, envelopeId, "part-repetido@example.com").andExpect(status().isCreated());

        assertThat(participantCount(envelopeId)).isEqualTo(1);
    }

    @Test
    @DisplayName("participante não lista, não convida e não remove: 403")
    void participanteNaoGerenciaCompartilhamento() throws Exception {
        String owner = verifiedToken("part-p-dono@example.com");
        String participant = verifiedToken("part-p-membro@example.com");
        verifiedToken("part-p-outro@example.com");
        String envelopeId = createEnvelope(owner, "Mercado");
        invite(owner, envelopeId, "part-p-membro@example.com").andExpect(status().isCreated());
        invite(owner, envelopeId, "part-p-outro@example.com").andExpect(status().isCreated());

        mockMvc.perform(get(PARTICIPANTS, envelopeId).header("Authorization", "Bearer " + participant))
                .andExpect(status().isForbidden());
        invite(participant, envelopeId, "part-p-dono@example.com").andExpect(status().isForbidden());
        mockMvc.perform(delete(PARTICIPANTS + "/{userId}", envelopeId, userId("part-p-outro@example.com"))
                        .header("Authorization", "Bearer " + participant))
                .andExpect(status().isForbidden());

        assertThat(participantCount(envelopeId)).isEqualTo(2);
    }

    @Test
    @DisplayName("terceiro sem vínculo não convida nem vê dados da verba")
    void terceiroNaoConvida() throws Exception {
        String owner = verifiedToken("part-t-dono@example.com");
        String stranger = verifiedToken("part-t-estranho@example.com");
        verifiedToken("part-t-alvo@example.com");
        String envelopeId = createEnvelope(owner, "Mercado Secreto");

        MvcResult result = invite(stranger, envelopeId, "part-t-alvo@example.com").andReturn();

        assertThat(result.getResponse().getStatus()).isIn(403, 404);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Mercado Secreto");
        assertThat(participantCount(envelopeId)).isZero();
    }

    @Test
    @DisplayName("verba arquivada responde 404")
    void verbaArquivadaRespondeNotFound() throws Exception {
        String owner = verifiedToken("part-arquivada@example.com");
        String envelopeId = createEnvelope(owner, "Antiga");
        mockMvc.perform(post("/api/v1/envelopes/{id}/archive", envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(PARTICIPANTS, envelopeId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("e-mail desconhecido e conta não verificada recebem a mesma mensagem genérica")
    void emailDesconhecidoENaoVerificadoTemMensagemGenerica() throws Exception {
        String owner = verifiedToken("part-generico@example.com");
        unverifiedToken("part-nao-verificado@example.com");
        String envelopeId = createEnvelope(owner, "Mercado");

        invite(owner, envelopeId, "ninguem@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(GENERIC_INVITE_FAILURE));
        invite(owner, envelopeId, "part-nao-verificado@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(GENERIC_INVITE_FAILURE));
    }

    @Test
    @DisplayName("recusa corpo sem e-mail com 400 INVALID_REQUEST")
    void recusaCorpoSemEmail() throws Exception {
        String owner = verifiedToken("part-sem-email@example.com");
        String envelopeId = createEnvelope(owner, "Mercado");

        mockMvc.perform(post(PARTICIPANTS, envelopeId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("recusa convidar o proprietário da verba")
    void recusaConvidarProprietario() throws Exception {
        String owner = verifiedToken("part-proprio@example.com");
        String envelopeId = createEnvelope(owner, "Mercado");

        invite(owner, envelopeId, "part-proprio@example.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Não é possível convidar o proprietário da verba"));
    }

    @Test
    @DisplayName("participante removido perde acesso à verba")
    void participanteRemovidoPerdeAcesso() throws Exception {
        String owner = verifiedToken("part-remove-dono@example.com");
        String participant = verifiedToken("part-removido@example.com");
        String envelopeId = createEnvelope(owner, "Mercado");
        invite(owner, envelopeId, "part-removido@example.com").andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/envelopes/{id}", envelopeId).header("Authorization", "Bearer " + participant))
                .andExpect(status().isOk());

        mockMvc.perform(delete(PARTICIPANTS + "/{userId}", envelopeId, userId("part-removido@example.com"))
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        int status = mockMvc.perform(get("/api/v1/envelopes/{id}", envelopeId)
                        .header("Authorization", "Bearer " + participant))
                .andReturn().getResponse().getStatus();
        assertThat(status).isIn(403, 404);
        assertThat(participantCount(envelopeId)).isZero();
    }

    @Test
    @DisplayName("exige sessão para listar, convidar e remover")
    void exigeSessao() throws Exception {
        UUID envelopeId = UUID.randomUUID();
        mockMvc.perform(get(PARTICIPANTS, envelopeId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PARTICIPANTS, envelopeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PARTICIPANTS + "/{userId}", envelopeId, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions invite(String token, String envelopeId, String email)
            throws Exception {
        return mockMvc.perform(post(PARTICIPANTS, envelopeId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email))));
    }

    private String createEnvelope(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/envelopes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "purpose", "LIMIT",
                                "baseAmount", Map.of("amount", "200.00", "currency", "BRL")))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asString();
    }

    private String verifiedToken(String email) throws Exception {
        String token = unverifiedToken(email);
        jdbc.update("UPDATE user_account SET email_verified_at = now() WHERE email_normalized = ?", email);
        return token;
    }

    private String unverifiedToken(String email) throws Exception {
        String credentials = objectMapper.writeValueAsString(new Credentials(email, PASSWORD));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isAccepted());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asString();
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email_normalized = ?", UUID.class, email);
    }

    private int participantCount(String envelopeId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM envelope_participant WHERE envelope_id = ?",
                Integer.class, UUID.fromString(envelopeId));
        return count == null ? 0 : count;
    }

    private record Credentials(String email, String password) {}
}
