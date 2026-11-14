package br.com.fiap.billing.orcamento.adapter.out.persistence;

import br.com.fiap.billing.orcamento.application.port.out.OrcamentoRepositoryPort;
import br.com.fiap.billing.orcamento.domain.model.Orcamento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrcamentoRepositoryAdapter implements OrcamentoRepositoryPort {

    private final OrcamentoMongoRepository mongoRepository;

    @Override
    public Orcamento salvar(Orcamento orcamento) {
        return mongoRepository.save(orcamento);
    }

    @Override
    public Optional<Orcamento> buscarPorOsId(String osId) {
        return mongoRepository.findByOsId(osId);
    }
}
