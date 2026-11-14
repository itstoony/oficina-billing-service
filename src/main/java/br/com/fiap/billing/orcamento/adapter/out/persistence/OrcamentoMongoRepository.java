package br.com.fiap.billing.orcamento.adapter.out.persistence;

import br.com.fiap.billing.orcamento.domain.model.Orcamento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OrcamentoMongoRepository extends MongoRepository<Orcamento, String> {
    Optional<Orcamento> findByOsId(String osId);
}
