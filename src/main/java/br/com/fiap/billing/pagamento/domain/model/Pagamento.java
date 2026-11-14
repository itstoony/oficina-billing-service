package br.com.fiap.billing.pagamento.domain.model;

import br.com.fiap.billing.pagamento.domain.valueobject.StatusPagamento;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "pagamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pagamento {

    @Id
    private String id;

    @Indexed(unique = true)
    private String osId;

    private String orcamentoId;
    private String externalId;
    private String preferenceId;
    private String checkoutUrl;

    @Builder.Default
    private BigDecimal valorPago = BigDecimal.ZERO;

    @Builder.Default
    private StatusPagamento status = StatusPagamento.PENDENTE;

    @Builder.Default
    private LocalDateTime dataCriacao = LocalDateTime.now();

    private LocalDateTime dataPagamento;
}
