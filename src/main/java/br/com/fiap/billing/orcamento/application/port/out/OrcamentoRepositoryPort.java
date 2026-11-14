package br.com.fiap.billing.orcamento.application.port.out;

import br.com.fiap.billing.orcamento.domain.model.Orcamento;

import java.util.Optional;

public interface OrcamentoRepositoryPort {
    Orcamento salvar(Orcamento orcamento);
    Optional<Orcamento> buscarPorOsId(String osId);
}
