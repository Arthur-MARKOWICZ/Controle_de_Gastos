package br.com.controlegastos.privacy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.controlegastos.identity.application.AccountClosureService;
import br.com.controlegastos.identity.application.AuthenticationService;
import br.com.controlegastos.identity.application.InvalidCredentialsException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TitularRightsServiceTest {

    private final AuthenticationService authentication = mock(AuthenticationService.class);
    private final AccountClosureService closure = mock(AccountClosureService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TitularRightsService service = new TitularRightsService(authentication, closure, jdbc);
    private final UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(authentication.currentUserId()).thenReturn(me);
        when(closure.exportIdentity(me)).thenReturn(Map.of("userId", me.toString()));
    }

    @Test
    @DisplayName("exporta as cinco seções do titular atual na ordem do relatório")
    void exportaSecoesNaOrdem() {
        Map<String, Object> exported = service.exportCurrentTitular();

        assertThat(exported.keySet())
                .containsExactly("identity", "income", "envelopes", "ledgerEntries", "ledgerEntryRevisions");
    }

    @Test
    @DisplayName("toda consulta de exportação é filtrada pelo titular autenticado")
    @SuppressWarnings("unchecked")
    void exportacaoFiltraPeloTitularAtual() {
        service.exportCurrentTitular();

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.times(4)).query(anyString(), any(RowMapper.class), args.capture());
        assertThat(args.getAllValues()).allSatisfy(params -> assertThat(params).containsOnly(me));
    }

    @Test
    @DisplayName("valores monetários saem como string decimal, sem conversão para double")
    @SuppressWarnings("unchecked")
    void valoresMonetariosComoTexto() {
        service.exportCurrentTitular();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(4)).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getAllValues()).allSatisfy(query -> assertThat(query).containsPattern("::text AS"));
    }

    @Test
    @DisplayName("eliminação apaga dados financeiros do titular e encerra a conta")
    void eliminacaoApagaEEncerra() {
        service.eraseCurrentTitular("senha");

        InOrder order = inOrder(jdbc, closure);
        order.verify(jdbc).update("DELETE FROM envelope WHERE owner_id = ?", me);
        order.verify(jdbc).update("DELETE FROM envelope_participant WHERE user_id = ?", me);
        order.verify(jdbc).update("DELETE FROM income_revision WHERE owner_id = ?", me);
        order.verify(jdbc).update("DELETE FROM monthly_income WHERE owner_id = ?", me);
        order.verify(closure).closeAccount(me, "senha");
    }

    @Test
    @DisplayName("senha errada na eliminação propaga InvalidCredentialsException para desfazer a transação")
    void senhaErradaPropaga() {
        doThrow(new InvalidCredentialsException()).when(closure).closeAccount(me, "errada");

        assertThatThrownBy(() -> service.eraseCurrentTitular("errada"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("titular inexistente na exportação lança InvalidCredentialsException")
    void titularInexistenteNaExportacao() {
        when(closure.exportIdentity(me)).thenThrow(new InvalidCredentialsException());

        assertThatThrownBy(service::exportCurrentTitular).isInstanceOf(InvalidCredentialsException.class);
        verify(jdbc, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    @DisplayName("eliminação nunca apaga dados de outro titular")
    void eliminacaoNaoTocaOutroTitular() {
        service.eraseCurrentTitular("senha");

        ArgumentCaptor<Object> param = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, org.mockito.Mockito.times(4)).update(anyString(), param.capture());
        assertThat(param.getAllValues()).containsOnly(me);
    }

    @Test
    @DisplayName("falha no banco durante a eliminação impede o encerramento da conta")
    void falhaNoBancoImpedeEncerramento() {
        when(jdbc.update(eq("DELETE FROM envelope WHERE owner_id = ?"), eq(me)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("fk"));

        assertThatThrownBy(() -> service.eraseCurrentTitular("senha"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        verify(closure, never()).closeAccount(any(), any());
    }
}
