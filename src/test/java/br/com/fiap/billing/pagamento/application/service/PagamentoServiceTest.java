package br.com.fiap.billing.pagamento.application.service;

import br.com.fiap.billing.orcamento.application.port.out.BillingEventPublisherPort;
import br.com.fiap.billing.orcamento.application.port.out.OrcamentoRepositoryPort;
import br.com.fiap.billing.orcamento.domain.model.Orcamento;
import br.com.fiap.billing.orcamento.domain.valueobject.StatusOrcamento;
import br.com.fiap.billing.pagamento.application.port.in.PagamentoDTO;
import br.com.fiap.billing.pagamento.application.port.out.MercadoPagoPort;
import br.com.fiap.billing.pagamento.application.port.out.PagamentoRepositoryPort;
import br.com.fiap.billing.pagamento.domain.model.Pagamento;
import br.com.fiap.billing.pagamento.domain.valueobject.StatusPagamento;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    @Mock private PagamentoRepositoryPort pagamentoRepositorio;
    @Mock private OrcamentoRepositoryPort orcamentoRepositorio;
    @Mock private MercadoPagoPort mercadoPago;
    @Mock private BillingEventPublisherPort eventPublisher;

    @InjectMocks
    private PagamentoService service;

    private Orcamento orcamento;

    @BeforeEach
    void setUp() {
        orcamento = Orcamento.builder()
                .id("orc-001")
                .osId("os-123")
                .numeroOs("OS-2026-00001")
                .clienteEmail("joao@email.com")
                .valorTotal(new BigDecimal("300.00"))
                .status(StatusOrcamento.ENVIADO)
                .build();
    }

    @Test
    @DisplayName("Deve iniciar pagamento com sucesso")
    void deveIniciarPagamento() {
        when(orcamentoRepositorio.buscarPorOsId("os-123")).thenReturn(Optional.of(orcamento));
        when(pagamentoRepositorio.buscarPorOsId("os-123")).thenReturn(Optional.empty());
        when(mercadoPago.criarPreferencia(any(), any(), any(), any()))
                .thenReturn(new MercadoPagoPort.CriarPreferenciaResult("pref-001", "https://mp.com/checkout"));
        when(pagamentoRepositorio.salvar(any())).thenAnswer(inv -> {
            Pagamento p = inv.getArgument(0);
            p.setId("pag-001");
            return p;
        });

        PagamentoDTO.Response response = service.iniciarPagamento(new PagamentoDTO.IniciarRequest("os-123"));

        assertThat(response.checkoutUrl()).isEqualTo("https://mp.com/checkout");
        assertThat(response.status()).isEqualTo("PENDENTE");
        verify(mercadoPago).criarPreferencia(eq("os-123"), any(), eq(new BigDecimal("300.00")), eq("joao@email.com"));
    }

    @Test
    @DisplayName("Deve lançar exceção ao iniciar pagamento sem orçamento")
    void deveLancarExcecaoSemOrcamento() {
        when(orcamentoRepositorio.buscarPorOsId("os-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.iniciarPagamento(new PagamentoDTO.IniciarRequest("os-inexistente")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção se orçamento estiver cancelado")
    void deveLancarExcecaoOrcamentoCancelado() {
        orcamento.setStatus(StatusOrcamento.CANCELADO);
        when(orcamentoRepositorio.buscarPorOsId("os-123")).thenReturn(Optional.of(orcamento));

        assertThatThrownBy(() -> service.iniciarPagamento(new PagamentoDTO.IniciarRequest("os-123")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    @DisplayName("Deve processar webhook de pagamento aprovado")
    void deveProcessarWebhookAprovado() {
        Pagamento pagamento = Pagamento.builder()
                .id("pag-001")
                .osId("os-123")
                .orcamentoId("orc-001")
                .status(StatusPagamento.PENDENTE)
                .valorPago(BigDecimal.ZERO)
                .build();

        when(pagamentoRepositorio.buscarPorExternalId("mp-456")).thenReturn(Optional.of(pagamento));
        when(orcamentoRepositorio.buscarPorOsId("os-123")).thenReturn(Optional.of(orcamento));
        when(mercadoPago.consultarPagamento("mp-456"))
                .thenReturn(new MercadoPagoPort.ConsultarPagamentoResult("approved", new BigDecimal("300.00")));
        when(pagamentoRepositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orcamentoRepositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventPublisher).publicarPagamentoConfirmado(any());

        PagamentoDTO.WebhookRequest webhook = new PagamentoDTO.WebhookRequest(
                "payment.updated", "payment", new PagamentoDTO.WebhookData("mp-456"));
        service.processarWebhook(webhook);

        verify(eventPublisher).publicarPagamentoConfirmado(any());
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
    }

    @Test
    @DisplayName("Deve processar webhook de pagamento recusado")
    void deveProcessarWebhookRecusado() {
        Pagamento pagamento = Pagamento.builder()
                .id("pag-002")
                .osId("os-456")
                .status(StatusPagamento.PENDENTE)
                .build();

        when(pagamentoRepositorio.buscarPorExternalId("mp-789")).thenReturn(Optional.of(pagamento));
        when(mercadoPago.consultarPagamento("mp-789"))
                .thenReturn(new MercadoPagoPort.ConsultarPagamentoResult("rejected", BigDecimal.ZERO));
        when(pagamentoRepositorio.salvar(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventPublisher).publicarPagamentoRecusado(any());

        PagamentoDTO.WebhookRequest webhook = new PagamentoDTO.WebhookRequest(
                "payment.updated", "payment", new PagamentoDTO.WebhookData("mp-789"));
        service.processarWebhook(webhook);

        verify(eventPublisher).publicarPagamentoRecusado(any());
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
    }
}
