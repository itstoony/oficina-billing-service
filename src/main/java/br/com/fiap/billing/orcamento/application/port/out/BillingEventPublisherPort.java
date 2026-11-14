package br.com.fiap.billing.orcamento.application.port.out;

import br.com.fiap.billing.orcamento.domain.model.Orcamento;
import br.com.fiap.billing.pagamento.domain.model.Pagamento;

public interface BillingEventPublisherPort {
    void publicarOrcamentoGerado(Orcamento orcamento);
    void publicarPagamentoConfirmado(Pagamento pagamento);
    void publicarPagamentoRecusado(Pagamento pagamento);
}
