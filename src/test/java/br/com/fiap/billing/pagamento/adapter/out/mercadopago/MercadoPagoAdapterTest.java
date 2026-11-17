package br.com.fiap.billing.pagamento.adapter.out.mercadopago;

import br.com.fiap.billing.pagamento.application.port.out.MercadoPagoPort;
import br.com.fiap.billing.shared.exception.RegraDeNegocioException;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResponse;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MercadoPagoAdapterTest {

    private MercadoPagoAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MercadoPagoAdapter();
        // injeta notificationUrl via reflection (campo @Value não inicializado fora do Spring)
        try {
            var field = MercadoPagoAdapter.class.getDeclaredField("notificationUrl");
            field.setAccessible(true);
            field.set(adapter, "http://localhost:8082/api/billing/pagamentos/webhook");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Deve criar preferência no MercadoPago com sucesso")
    void deveCriarPreferencia() throws Exception {
        Preference preference = mock(Preference.class);
        when(preference.getId()).thenReturn("pref-id-123");
        when(preference.getInitPoint()).thenReturn("https://mp.com/checkout/123");

        try (MockedConstruction<PreferenceClient> mocked =
                     mockConstruction(PreferenceClient.class,
                             (client, ctx) -> when(client.create(any())).thenReturn(preference))) {

            MercadoPagoPort.CriarPreferenciaResult result =
                    adapter.criarPreferencia("os-123", "Serviço Oficina", new BigDecimal("200.00"), "cliente@email.com");

            assertThat(result.preferenceId()).isEqualTo("pref-id-123");
            assertThat(result.checkoutUrl()).isEqualTo("https://mp.com/checkout/123");
        }
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException quando MPApiException ocorrer")
    void deveLancarExcecaoQuandoMPApiException() throws Exception {
        MPResponse mpResponse = mock(MPResponse.class);
        when(mpResponse.getContent()).thenReturn("{\"message\":\"invalid email\"}");
        MPApiException apiException = new MPApiException("Api error. Check response for details", mpResponse);

        try (MockedConstruction<PreferenceClient> mocked =
                     mockConstruction(PreferenceClient.class,
                             (client, ctx) -> when(client.create(any())).thenThrow(apiException))) {

            assertThatThrownBy(() ->
                    adapter.criarPreferencia("os-123", "Serviço", new BigDecimal("100.00"), "email@test.com"))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Erro ao criar pagamento");
        }
    }

    @Test
    @DisplayName("Deve lançar RegraDeNegocioException quando MPException ocorrer")
    void deveLancarExcecaoQuandoMPException() throws Exception {
        MPException mpException = new MPException("Connection error");

        try (MockedConstruction<PreferenceClient> mocked =
                     mockConstruction(PreferenceClient.class,
                             (client, ctx) -> when(client.create(any())).thenThrow(mpException))) {

            assertThatThrownBy(() ->
                    adapter.criarPreferencia("os-123", "Serviço", new BigDecimal("100.00"), "email@test.com"))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Erro ao criar pagamento");
        }
    }

    @Test
    @DisplayName("Deve consultar pagamento com sucesso")
    void deveConsultarPagamento() throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("approved");
        when(payment.getTransactionAmount()).thenReturn(new BigDecimal("150.00"));

        try (MockedConstruction<PaymentClient> mocked =
                     mockConstruction(PaymentClient.class,
                             (client, ctx) -> when(client.get(anyLong())).thenReturn(payment))) {

            MercadoPagoPort.ConsultarPagamentoResult result = adapter.consultarPagamento("12345");

            assertThat(result.status()).isEqualTo("approved");
            assertThat(result.valor()).isEqualByComparingTo(new BigDecimal("150.00"));
        }
    }

    @Test
    @DisplayName("Deve retornar status error quando externalId não for número válido")
    void deveRetornarErrorQuandoExternalIdInvalido() {
        MercadoPagoPort.ConsultarPagamentoResult result = adapter.consultarPagamento("nao-e-numero");

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.valor()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Deve retornar error quando MPException ocorrer na consulta")
    void deveRetornarErrorQuandoMPExceptionNaConsulta() throws Exception {
        MPException mpException = new MPException("timeout");

        try (MockedConstruction<PaymentClient> mocked =
                     mockConstruction(PaymentClient.class,
                             (client, ctx) -> when(client.get(anyLong())).thenThrow(mpException))) {

            MercadoPagoPort.ConsultarPagamentoResult result = adapter.consultarPagamento("99999");

            assertThat(result.status()).isEqualTo("error");
            assertThat(result.valor()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("Deve retornar zero quando transactionAmount for null")
    void deveRetornarZeroQuandoTransactionAmountNull() throws Exception {
        Payment payment = mock(Payment.class);
        when(payment.getStatus()).thenReturn("pending");
        when(payment.getTransactionAmount()).thenReturn(null);

        try (MockedConstruction<PaymentClient> mocked =
                     mockConstruction(PaymentClient.class,
                             (client, ctx) -> when(client.get(anyLong())).thenReturn(payment))) {

            MercadoPagoPort.ConsultarPagamentoResult result = adapter.consultarPagamento("11111");

            assertThat(result.status()).isEqualTo("pending");
            assertThat(result.valor()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
