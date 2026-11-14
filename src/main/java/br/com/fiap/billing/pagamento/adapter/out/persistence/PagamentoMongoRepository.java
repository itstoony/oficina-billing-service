package br.com.fiap.billing.pagamento.adapter.out.persistence;

import br.com.fiap.billing.pagamento.domain.model.Pagamento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PagamentoMongoRepository extends MongoRepository<Pagamento, String> {
    Optional<Pagamento> findByOsId(String osId);
    Optional<Pagamento> findByExternalId(String externalId);
}
