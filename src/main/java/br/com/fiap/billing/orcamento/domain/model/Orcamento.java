package br.com.fiap.billing.orcamento.domain.model;

import br.com.fiap.billing.orcamento.domain.valueobject.StatusOrcamento;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "orcamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Orcamento {

    @Id
    private String id;

    @Indexed(unique = true)
    private String osId;

    private String numeroOs;
    private String clienteId;
    private String clienteEmail;

    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Builder.Default
    private StatusOrcamento status = StatusOrcamento.PENDENTE;

    @Builder.Default
    private List<ItemOrcamento> itensServico = new ArrayList<>();

    @Builder.Default
    private List<ItemOrcamento> itensPeca = new ArrayList<>();

    @Builder.Default
    private LocalDateTime dataCriacao = LocalDateTime.now();

    private LocalDateTime dataAtualizacao;
}
