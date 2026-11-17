package br.com.fiap.billing.pagamento.application.service;

import br.com.fiap.billing.orcamento.application.port.out.BillingEventPublisherPort;
import br.com.fiap.billing.orcamento.application.port.out.OrcamentoRepositoryPort;
import br.com.fiap.billing.orcamento.domain.model.Orcamento;
import br.com.fiap.billing.orcamento.domain.valueobject.StatusOrcamento;
import br.com.fiap.billing.pagamento.application.port.in.PagamentoDTO;
import br.com.fiap.billing.pagamento.application.port.in.PagamentoUseCase;
import br.com.fiap.billing.pagamento.application.port.out.MercadoPagoPort;
import br.com.fiap.billing.pagamento.application.port.out.PagamentoRepositoryPort;
import br.com.fiap.billing.pagamento.domain.model.Pagamento;
import br.com.fiap.billing.pagamento.domain.valueobject.StatusPagamento;
import br.com.fiap.billing.shared.exception.RecursoNaoEncontradoException;
import br.com.fiap.billing.shared.exception.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagamentoService implements PagamentoUseCase {

    private final PagamentoRepositoryPort pagamentoRepositorio;
    private final OrcamentoRepositoryPort orcamentoRepositorio;
    private final MercadoPagoPort mercadoPago;
    private final BillingEventPublisherPort eventPublisher;

    @Override
    public PagamentoDTO.Response iniciarPagamento(PagamentoDTO.IniciarRequest request) {
        Orcamento orcamento = orcamentoRepositorio.buscarPorOsId(request.osId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Orçamento não encontrado para OS: " + request.osId()));

        if (orcamento.getStatus() == StatusOrcamento.CANCELADO) {
            throw new RegraDeNegocioException("Não é possível iniciar pagamento de orçamento cancelado");
        }

        pagamentoRepositorio.buscarPorOsId(request.osId()).ifPresent(existente -> {
            if (existente.getStatus() == StatusPagamento.APROVADO) {
                throw new RegraDeNegocioException("Pagamento já foi aprovado para OS: " + request.osId());
            }
        });

        String descricao = "Ordem de Serviço " + orcamento.getNumeroOs();
        MercadoPagoPort.CriarPreferenciaResult preferencia = mercadoPago.criarPreferencia(
                request.osId(), descricao, orcamento.getValorTotal(), orcamento.getClienteEmail());

        Pagamento pagamento = Pagamento.builder()
                .osId(request.osId())
                .orcamentoId(orcamento.getId())
                .preferenceId(preferencia.preferenceId())
                .checkoutUrl(preferencia.checkoutUrl())
                .valorPago(orcamento.getValorTotal())
                .status(StatusPagamento.PENDENTE)
                .build();

        return toResponse(pagamentoRepositorio.salvar(pagamento));
    }

    @Override
    public PagamentoDTO.Response buscarPorOsId(String osId) {
        Pagamento pagamento = pagamentoRepositorio.buscarPorOsId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado para OS: " + osId));
        return toResponse(pagamento);
    }

    @Override
    public PagamentoDTO.Response confirmarPagamentoTeste(String osId) {
        Pagamento pagamento = pagamentoRepositorio.buscarPorOsId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado para OS: " + osId));

        pagamento.setStatus(StatusPagamento.APROVADO);
        pagamento.setExternalId("TESTE-" + osId.substring(0, 8));
        pagamento.setDataPagamento(LocalDateTime.now());

        pagamentoRepositorio.salvar(pagamento);
        atualizarOrcamento(osId, StatusOrcamento.APROVADO);
        publicarEventoSeguro(() -> eventPublisher.publicarPagamentoConfirmado(pagamento), osId);

        log.info("Pagamento de teste confirmado para OS {}", osId);
        return toResponse(pagamento);
    }

    @Override
    public void processarWebhook(PagamentoDTO.WebhookRequest webhook) {
        if (webhook.data() == null || webhook.data().id() == null) return;

        String externalId = webhook.data().id();
        log.info("Webhook recebido — tipo: {}, id: {}", webhook.type(), externalId);

        MercadoPagoPort.ConsultarPagamentoResult resultado = mercadoPago.consultarPagamento(externalId);

        pagamentoRepositorio.buscarPorExternalId(externalId).ifPresent(pagamento -> {
            processarResultado(pagamento, resultado, externalId);
        });

        if (pagamentoRepositorio.buscarPorExternalId(externalId).isEmpty()) {
            log.debug("Pagamento com externalId {} não encontrado na base", externalId);
        }
    }

    private void processarResultado(Pagamento pagamento,
                                    MercadoPagoPort.ConsultarPagamentoResult resultado,
                                    String externalId) {
        String statusMp = resultado.status();
        if ("approved".equals(statusMp)) {
            pagamento.setStatus(StatusPagamento.APROVADO);
            pagamento.setExternalId(externalId);
            pagamento.setDataPagamento(LocalDateTime.now());
            pagamento.setValorPago(resultado.valor());
            pagamentoRepositorio.salvar(pagamento);
            atualizarOrcamento(pagamento.getOsId(), StatusOrcamento.APROVADO);
            publicarEventoSeguro(() -> eventPublisher.publicarPagamentoConfirmado(pagamento), pagamento.getOsId());
            log.info("Pagamento aprovado para OS {}", pagamento.getOsId());
        } else if ("rejected".equals(statusMp) || "cancelled".equals(statusMp)) {
            pagamento.setStatus(StatusPagamento.RECUSADO);
            pagamento.setExternalId(externalId);
            pagamentoRepositorio.salvar(pagamento);
            publicarEventoSeguro(() -> eventPublisher.publicarPagamentoRecusado(pagamento), pagamento.getOsId());
            log.info("Pagamento recusado para OS {}", pagamento.getOsId());
        }
    }

    private void atualizarOrcamento(String osId, StatusOrcamento novoStatus) {
        orcamentoRepositorio.buscarPorOsId(osId).ifPresent(orcamento -> {
            orcamento.setStatus(novoStatus);
            orcamento.setDataAtualizacao(LocalDateTime.now());
            orcamentoRepositorio.salvar(orcamento);
        });
    }

    private void publicarEventoSeguro(Runnable publicacao, String osId) {
        try {
            publicacao.run();
        } catch (Exception e) {
            log.warn("Falha ao publicar evento de pagamento para OS {}: {}", osId, e.getMessage());
        }
    }

    private PagamentoDTO.Response toResponse(Pagamento pagamento) {
        return new PagamentoDTO.Response(
                pagamento.getId(),
                pagamento.getOsId(),
                pagamento.getOrcamentoId(),
                pagamento.getExternalId(),
                pagamento.getCheckoutUrl(),
                pagamento.getValorPago(),
                pagamento.getStatus().name(),
                pagamento.getDataCriacao(),
                pagamento.getDataPagamento()
        );
    }
}
