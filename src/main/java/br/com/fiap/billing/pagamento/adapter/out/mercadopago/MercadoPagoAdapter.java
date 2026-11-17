package br.com.fiap.billing.pagamento.adapter.out.mercadopago;

import br.com.fiap.billing.pagamento.application.port.out.MercadoPagoPort;
import br.com.fiap.billing.shared.exception.RegraDeNegocioException;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
public class MercadoPagoAdapter implements MercadoPagoPort {

    @Value("${mercadopago.notification-url}")
    private String notificationUrl;

    @Override
    public CriarPreferenciaResult criarPreferencia(String osId,
                                                    String descricao,
                                                    BigDecimal valor,
                                                    String emailPagador) {
        try {
            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title(descricao)
                    .quantity(1)
                    .unitPrice(valor)
                    .build();

            PreferencePayerRequest payer = PreferencePayerRequest.builder()
                    .email(emailPagador)
                    .build();

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(notificationUrl + "?os=" + osId + "&status=success")
                    .failure(notificationUrl + "?os=" + osId + "&status=failure")
                    .pending(notificationUrl + "?os=" + osId + "&status=pending")
                    .build();

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(List.of(item))
                    .payer(payer)
                    .backUrls(backUrls)
                    .notificationUrl(notificationUrl)
                    .externalReference(osId)
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(request);

            return new CriarPreferenciaResult(preference.getId(), preference.getInitPoint());
        } catch (MPApiException e) {
            log.error("Erro ao criar preferência no Mercado Pago para OS {} — status: {}, body: {}",
                    osId, e.getStatusCode(), e.getApiResponse() != null ? e.getApiResponse().getContent() : "null");
            throw new RegraDeNegocioException("Erro ao criar pagamento: " + e.getMessage());
        } catch (MPException e) {
            log.error("Erro ao criar preferência no Mercado Pago para OS {}: {}", osId, e.getMessage());
            throw new RegraDeNegocioException("Erro ao criar pagamento: " + e.getMessage());
        }
    }

    @Override
    public ConsultarPagamentoResult consultarPagamento(String externalId) {
        try {
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(externalId));
            BigDecimal valor = payment.getTransactionAmount() != null
                    ? payment.getTransactionAmount()
                    : BigDecimal.ZERO;
            return new ConsultarPagamentoResult(payment.getStatus(), valor);
        } catch (MPException | MPApiException e) {
            log.error("Erro ao consultar pagamento {} no Mercado Pago: {}", externalId, e.getMessage());
            return new ConsultarPagamentoResult("error", BigDecimal.ZERO);
        } catch (NumberFormatException e) {
            log.error("ExternalId inválido para Mercado Pago: {}", externalId);
            return new ConsultarPagamentoResult("error", BigDecimal.ZERO);
        }
    }
}
