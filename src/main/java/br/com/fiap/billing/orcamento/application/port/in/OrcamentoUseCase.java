package br.com.fiap.billing.orcamento.application.port.in;

public interface OrcamentoUseCase {
    OrcamentoDTO.Response gerar(OrcamentoDTO.GerarRequest request);
    OrcamentoDTO.Response buscarPorOsId(String osId);
}
