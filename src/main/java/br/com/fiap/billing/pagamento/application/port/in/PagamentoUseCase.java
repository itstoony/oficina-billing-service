package br.com.fiap.billing.pagamento.application.port.in;

public interface PagamentoUseCase {
    PagamentoDTO.Response iniciarPagamento(PagamentoDTO.IniciarRequest request);
    PagamentoDTO.Response buscarPorOsId(String osId);
    void processarWebhook(PagamentoDTO.WebhookRequest webhook);
    PagamentoDTO.Response confirmarPagamentoTeste(String osId);
}
