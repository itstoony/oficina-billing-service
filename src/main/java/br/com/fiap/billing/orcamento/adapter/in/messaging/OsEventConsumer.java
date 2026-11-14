package br.com.fiap.billing.orcamento.adapter.in.messaging;

import br.com.fiap.billing.config.RabbitMQConfig;
import br.com.fiap.billing.orcamento.application.port.in.OrcamentoDTO;
import br.com.fiap.billing.orcamento.application.port.in.OrcamentoUseCase;
import br.com.fiap.billing.shared.exception.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OsEventConsumer {

    private final OrcamentoUseCase orcamentoUseCase;

    @RabbitListener(queues = RabbitMQConfig.BILLING_QUEUE)
    public void consumir(Map<String, Object> evento,
                         @Header("amqp_receivedRoutingKey") String routingKey) {
        log.info("Evento recebido no billing: {}", routingKey);
        try {
            switch (routingKey) {
                case RabbitMQConfig.ROUTING_OS_ABERTA -> processarOsAberta(evento);
                case RabbitMQConfig.ROUTING_ORCAMENTO_SOLICITADO -> processarOrcamentoSolicitado(evento);
                default -> log.debug("Evento ignorado pelo billing-service: {}", routingKey);
            }
        } catch (RegraDeNegocioException e) {
            log.warn("Regra de negócio ao processar {}: {}", routingKey, e.getMessage());
        } catch (Exception e) {
            log.error("Erro ao processar evento {}: {}", routingKey, e.getMessage(), e);
        }
    }

    private void processarOsAberta(Map<String, Object> evento) {
        String osId = (String) evento.get("osId");
        String numeroOs = (String) evento.get("numero");
        String clienteId = (String) evento.get("clienteId");
        String nomeCliente = (String) evento.getOrDefault("nomeCliente", "");

        if (osId == null) return;

        OrcamentoDTO.GerarRequest request = new OrcamentoDTO.GerarRequest(
                osId, numeroOs, clienteId, nomeCliente + "@pendente.com",
                BigDecimal.ZERO, List.of(), List.of()
        );
        orcamentoUseCase.gerar(request);
        log.info("Orçamento inicial criado para OS {}", numeroOs);
    }

    @SuppressWarnings("unchecked")
    private void processarOrcamentoSolicitado(Map<String, Object> evento) {
        String osId = (String) evento.get("osId");
        String numeroOs = (String) evento.get("numero");
        String clienteId = (String) evento.get("clienteId");
        String clienteEmail = (String) evento.getOrDefault("clienteEmail", "");
        Object valorTotalObj = evento.get("valorTotal");
        BigDecimal valorTotal = valorTotalObj != null
                ? new BigDecimal(valorTotalObj.toString())
                : BigDecimal.ZERO;

        List<Map<String, Object>> servicosRaw = (List<Map<String, Object>>) evento.getOrDefault("servicos", List.of());
        List<Map<String, Object>> pecasRaw = (List<Map<String, Object>>) evento.getOrDefault("pecas", List.of());

        List<OrcamentoDTO.ItemRequest> itensServico = servicosRaw.stream()
                .map(this::toItemRequest)
                .toList();
        List<OrcamentoDTO.ItemRequest> itensPeca = pecasRaw.stream()
                .map(this::toItemRequest)
                .toList();

        OrcamentoDTO.GerarRequest request = new OrcamentoDTO.GerarRequest(
                osId, numeroOs, clienteId, clienteEmail,
                valorTotal, itensServico, itensPeca
        );
        orcamentoUseCase.gerar(request);
        log.info("Orçamento detalhado criado para OS {}", numeroOs);
    }

    private OrcamentoDTO.ItemRequest toItemRequest(Map<String, Object> item) {
        return new OrcamentoDTO.ItemRequest(
                (String) item.get("servicoId") != null ? (String) item.get("servicoId") : (String) item.get("pecaId"),
                (String) item.get("nome"),
                item.get("quantidade") instanceof Integer i ? i : Integer.parseInt(item.get("quantidade").toString()),
                new BigDecimal(item.get("precoUnitario").toString())
        );
    }
}
