package br.com.fiap.billing.orcamento.application.service;

import br.com.fiap.billing.orcamento.application.port.in.OrcamentoDTO;
import br.com.fiap.billing.orcamento.application.port.out.BillingEventPublisherPort;
import br.com.fiap.billing.orcamento.application.port.out.OrcamentoRepositoryPort;
import br.com.fiap.billing.orcamento.domain.model.Orcamento;
import br.com.fiap.billing.orcamento.domain.valueobject.StatusOrcamento;
import br.com.fiap.billing.shared.exception.RecursoNaoEncontradoException;
import br.com.fiap.billing.shared.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrcamentoServiceTest {

    @Mock private OrcamentoRepositoryPort repositorio;
    @Mock private BillingEventPublisherPort eventPublisher;

    @InjectMocks
    private OrcamentoService service;

    private OrcamentoDTO.GerarRequest requestValido;
    private Orcamento orcamentoSalvo;

    @BeforeEach
    void setUp() {
        requestValido = new OrcamentoDTO.GerarRequest(
                "os-uuid-123",
                "OS-2026-00001",
                "cliente-uuid-456",
                "joao@email.com",
                new BigDecimal("350.00"),
                List.of(new OrcamentoDTO.ItemRequest("serv-1", "Troca de óleo", 1, new BigDecimal("150.00"))),
                List.of(new OrcamentoDTO.ItemRequest("peca-1", "Filtro de óleo", 2, new BigDecimal("100.00")))
        );

        orcamentoSalvo = Orcamento.builder()
                .id("mongo-id-001")
                .osId("os-uuid-123")
                .numeroOs("OS-2026-00001")
                .clienteId("cliente-uuid-456")
                .clienteEmail("joao@email.com")
                .valorTotal(new BigDecimal("350.00"))
                .status(StatusOrcamento.ENVIADO)
                .build();
    }

    @Test
    @DisplayName("Deve gerar orçamento com sucesso")
    void deveGerarOrcamento() {
        when(repositorio.buscarPorOsId("os-uuid-123")).thenReturn(Optional.empty());
        when(repositorio.salvar(any())).thenReturn(orcamentoSalvo);
        doNothing().when(eventPublisher).publicarOrcamentoGerado(any());

        OrcamentoDTO.Response response = service.gerar(requestValido);

        assertThat(response.osId()).isEqualTo("os-uuid-123");
        assertThat(response.status()).isEqualTo("ENVIADO");
        verify(repositorio).salvar(any());
        verify(eventPublisher).publicarOrcamentoGerado(any());
    }

    @Test
    @DisplayName("Deve lançar exceção se orçamento já existe para a OS")
    void deveLancarExcecaoOrcamentoDuplicado() {
        when(repositorio.buscarPorOsId("os-uuid-123")).thenReturn(Optional.of(orcamentoSalvo));

        assertThatThrownBy(() -> service.gerar(requestValido))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("os-uuid-123");
    }

    @Test
    @DisplayName("Deve buscar orçamento por osId")
    void deveBuscarOrcamentoPorOsId() {
        when(repositorio.buscarPorOsId("os-uuid-123")).thenReturn(Optional.of(orcamentoSalvo));

        OrcamentoDTO.Response response = service.buscarPorOsId("os-uuid-123");

        assertThat(response.osId()).isEqualTo("os-uuid-123");
        assertThat(response.numeroOs()).isEqualTo("OS-2026-00001");
    }

    @Test
    @DisplayName("Deve lançar exceção ao buscar orçamento inexistente")
    void deveLancarExcecaoOrcamentoNaoEncontrado() {
        when(repositorio.buscarPorOsId("inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorOsId("inexistente"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve calcular valor total corretamente com itens")
    void deveCalcularValorTotal() {
        Orcamento orcamentoComValor = Orcamento.builder()
                .id("id-002")
                .osId("os-uuid-999")
                .valorTotal(new BigDecimal("350.00"))
                .status(StatusOrcamento.ENVIADO)
                .build();

        when(repositorio.buscarPorOsId("os-uuid-999")).thenReturn(Optional.empty());
        when(repositorio.salvar(any())).thenAnswer(inv -> {
            Orcamento arg = inv.getArgument(0);
            arg.setId("id-002");
            return arg;
        });
        doNothing().when(eventPublisher).publicarOrcamentoGerado(any());

        OrcamentoDTO.GerarRequest req = new OrcamentoDTO.GerarRequest(
                "os-uuid-999", "OS-2026-00002", "cliente-002", "email@test.com",
                BigDecimal.ZERO,
                List.of(new OrcamentoDTO.ItemRequest("s1", "Serviço A", 2, new BigDecimal("100.00"))),
                List.of(new OrcamentoDTO.ItemRequest("p1", "Peça B", 1, new BigDecimal("50.00")))
        );

        OrcamentoDTO.Response response = service.gerar(req);

        assertThat(response.valorTotal()).isEqualByComparingTo(new BigDecimal("250.00"));
    }
}
