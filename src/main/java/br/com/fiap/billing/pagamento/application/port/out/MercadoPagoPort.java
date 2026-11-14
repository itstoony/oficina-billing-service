package br.com.fiap.billing.pagamento.application.port.out;

import java.math.BigDecimal;

public interface MercadoPagoPort {
    CriarPreferenciaResult criarPreferencia(String osId, String descricao, BigDecimal valor, String emailPagador);
    ConsultarPagamentoResult consultarPagamento(String externalId);

    record CriarPreferenciaResult(String preferenceId, String checkoutUrl) {}
    record ConsultarPagamentoResult(String status, BigDecimal valor) {}
}
