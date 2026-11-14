package br.com.fiap.billing.pagamento.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PagamentoDTO {

    public record IniciarRequest(
            String osId
    ) {}

    public record Response(
            String id,
            String osId,
            String orcamentoId,
            String externalId,
            String checkoutUrl,
            BigDecimal valorPago,
            String status,
            LocalDateTime dataCriacao,
            LocalDateTime dataPagamento
    ) {}

    public record WebhookRequest(
            String action,
            String type,
            WebhookData data
    ) {}

    public record WebhookData(String id) {}
}
