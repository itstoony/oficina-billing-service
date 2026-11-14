package br.com.fiap.billing.pagamento.adapter.out.persistence;

import br.com.fiap.billing.pagamento.application.port.out.PagamentoRepositoryPort;
import br.com.fiap.billing.pagamento.domain.model.Pagamento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PagamentoRepositoryAdapter implements PagamentoRepositoryPort {

    private final PagamentoMongoRepository mongoRepository;

    @Override
    public Pagamento salvar(Pagamento pagamento) {
        return mongoRepository.save(pagamento);
    }

    @Override
    public Optional<Pagamento> buscarPorOsId(String osId) {
        return mongoRepository.findByOsId(osId);
    }

    @Override
    public Optional<Pagamento> buscarPorExternalId(String externalId) {
        return mongoRepository.findByExternalId(externalId);
    }
}
