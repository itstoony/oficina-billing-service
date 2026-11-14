package br.com.fiap.billing.orcamento.adapter.out.messaging;

import br.com.fiap.billing.config.RabbitMQConfig;
import br.com.fiap.billing.orcamento.application.port.out.BillingEventPublisherPort;
import br.com.fiap.billing.orcamento.domain.model.Orcamento;
import br.com.fiap.billing.pagamento.domain.model.Pagamento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingEventPublisher implements BillingEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publicarOrcamentoGerado(Orcamento orcamento) {
        Map<String, Object> evento = Map.of(
                "osId", orcamento.getOsId(),
                "orcamentoId", orcamento.getId(),
                "numeroOs", orcamento.getNumeroOs(),
                "clienteEmail", orcamento.getClienteEmail(),
                "valorTotal", orcamento.getValorTotal()
        );
        publicar("orcamento.gerado", evento);
    }

    @Override
    public void publicarPagamentoConfirmado(Pagamento pagamento) {
        Map<String, Object> evento = Map.of(
                "osId", pagamento.getOsId(),
                "pagamentoId", pagamento.getId(),
                "externalId", pagamento.getExternalId() != null ? pagamento.getExternalId() : "",
                "valorPago", pagamento.getValorPago()
        );
        publicar("pagamento.confirmado", evento);
    }

    @Override
    public void publicarPagamentoRecusado(Pagamento pagamento) {
        Map<String, Object> evento = Map.of(
                "osId", pagamento.getOsId(),
                "pagamentoId", pagamento.getId()
        );
        publicar("pagamento.recusado", evento);
    }

    private void publicar(String routingKey, Object payload) {
        log.info("Publicando evento: {}", routingKey);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, payload);
    }
}
