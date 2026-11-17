package br.com.fiap.billing.orcamento.application.service;

import br.com.fiap.billing.orcamento.application.port.in.OrcamentoDTO;
import br.com.fiap.billing.orcamento.application.port.in.OrcamentoUseCase;
import br.com.fiap.billing.orcamento.application.port.out.BillingEventPublisherPort;
import br.com.fiap.billing.orcamento.application.port.out.OrcamentoRepositoryPort;
import br.com.fiap.billing.orcamento.domain.model.ItemOrcamento;
import br.com.fiap.billing.orcamento.domain.model.Orcamento;
import br.com.fiap.billing.orcamento.domain.valueobject.StatusOrcamento;
import br.com.fiap.billing.shared.exception.RecursoNaoEncontradoException;
import br.com.fiap.billing.shared.exception.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrcamentoService implements OrcamentoUseCase {

    private final OrcamentoRepositoryPort repositorio;
    private final BillingEventPublisherPort eventPublisher;

    @Override
    public OrcamentoDTO.Response gerar(OrcamentoDTO.GerarRequest request) {
        var existente = repositorio.buscarPorOsId(request.osId());
        if (existente.isPresent()) {
            return atualizar(existente.get(), request);
        }

        List<ItemOrcamento> itensServico = toItens(request.itensServico());
        List<ItemOrcamento> itensPeca = toItens(request.itensPeca());

        BigDecimal valorTotal = calcularTotal(itensServico, itensPeca);

        Orcamento orcamento = Orcamento.builder()
                .osId(request.osId())
                .numeroOs(request.numeroOs())
                .clienteId(request.clienteId())
                .clienteEmail(request.clienteEmail())
                .valorTotal(valorTotal)
                .status(StatusOrcamento.ENVIADO)
                .itensServico(itensServico)
                .itensPeca(itensPeca)
                .build();

        Orcamento salvo = repositorio.salvar(orcamento);
        publicarEventoSeguro(() -> eventPublisher.publicarOrcamentoGerado(salvo), salvo.getOsId());
        return toResponse(salvo);
    }

    @Override
    public OrcamentoDTO.Response buscarPorOsId(String osId) {
        Orcamento orcamento = repositorio.buscarPorOsId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orçamento não encontrado para OS: " + osId));
        return toResponse(orcamento);
    }

    private OrcamentoDTO.Response atualizar(Orcamento orcamento, OrcamentoDTO.GerarRequest request) {
        List<ItemOrcamento> itensServico = toItens(request.itensServico());
        List<ItemOrcamento> itensPeca = toItens(request.itensPeca());
        BigDecimal valorTotal = calcularTotal(itensServico, itensPeca);

        if (!request.itensServico().isEmpty() || !request.itensPeca().isEmpty()) {
            orcamento.setItensServico(itensServico);
            orcamento.setItensPeca(itensPeca);
            orcamento.setValorTotal(valorTotal);
        }
        if (request.clienteEmail() != null && !request.clienteEmail().contains(" ")) {
            orcamento.setClienteEmail(request.clienteEmail());
        }
        orcamento.setDataAtualizacao(LocalDateTime.now());

        Orcamento salvo = repositorio.salvar(orcamento);
        publicarEventoSeguro(() -> eventPublisher.publicarOrcamentoGerado(salvo), salvo.getOsId());
        log.info("Orçamento atualizado para OS {}", salvo.getNumeroOs());
        return toResponse(salvo);
    }

    private List<ItemOrcamento> toItens(List<OrcamentoDTO.ItemRequest> itensRequest) {
        if (itensRequest == null) return List.of();
        return itensRequest.stream()
                .map(item -> ItemOrcamento.builder()
                        .referenciaId(item.referenciaId())
                        .nome(item.nome())
                        .quantidade(item.quantidade())
                        .precoUnitario(item.precoUnitario())
                        .subtotal(item.precoUnitario().multiply(BigDecimal.valueOf(item.quantidade())))
                        .build())
                .toList();
    }

    private BigDecimal calcularTotal(List<ItemOrcamento> servicos, List<ItemOrcamento> pecas) {
        BigDecimal totalServicos = servicos.stream()
                .map(ItemOrcamento::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPecas = pecas.stream()
                .map(ItemOrcamento::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalServicos.add(totalPecas);
    }

    private void publicarEventoSeguro(Runnable publicacao, String osId) {
        try {
            publicacao.run();
        } catch (Exception e) {
            log.warn("Falha ao publicar evento orcamento.gerado para OS {}: {}", osId, e.getMessage());
        }
    }

    private OrcamentoDTO.Response toResponse(Orcamento orcamento) {
        List<OrcamentoDTO.ItemResponse> itensServico = orcamento.getItensServico().stream()
                .map(this::toItemResponse)
                .toList();
        List<OrcamentoDTO.ItemResponse> itensPeca = orcamento.getItensPeca().stream()
                .map(this::toItemResponse)
                .toList();
        return new OrcamentoDTO.Response(
                orcamento.getId(),
                orcamento.getOsId(),
                orcamento.getNumeroOs(),
                orcamento.getClienteId(),
                orcamento.getClienteEmail(),
                orcamento.getValorTotal(),
                orcamento.getStatus().name(),
                itensServico,
                itensPeca,
                orcamento.getDataCriacao(),
                orcamento.getDataAtualizacao()
        );
    }

    private OrcamentoDTO.ItemResponse toItemResponse(ItemOrcamento item) {
        return new OrcamentoDTO.ItemResponse(
                item.getReferenciaId(),
                item.getNome(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getSubtotal()
        );
    }
}
