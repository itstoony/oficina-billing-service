package br.com.fiap.billing.orcamento.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrcamentoDTO {

    public record ItemResponse(
            String referenciaId,
            String nome,
            Integer quantidade,
            BigDecimal precoUnitario,
            BigDecimal subtotal
    ) {}

    public record Response(
            String id,
            String osId,
            String numeroOs,
            String clienteId,
            String clienteEmail,
            BigDecimal valorTotal,
            String status,
            List<ItemResponse> itensServico,
            List<ItemResponse> itensPeca,
            LocalDateTime dataCriacao,
            LocalDateTime dataAtualizacao
    ) {}

    public record GerarRequest(
            String osId,
            String numeroOs,
            String clienteId,
            String clienteEmail,
            BigDecimal valorTotal,
            List<ItemRequest> itensServico,
            List<ItemRequest> itensPeca
    ) {}

    public record ItemRequest(
            String referenciaId,
            String nome,
            Integer quantidade,
            BigDecimal precoUnitario
    ) {}
}
