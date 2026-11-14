package br.com.fiap.billing.pagamento.application.port.out;

import br.com.fiap.billing.pagamento.domain.model.Pagamento;

import java.util.Optional;

public interface PagamentoRepositoryPort {
    Pagamento salvar(Pagamento pagamento);
    Optional<Pagamento> buscarPorOsId(String osId);
    Optional<Pagamento> buscarPorExternalId(String externalId);
}
