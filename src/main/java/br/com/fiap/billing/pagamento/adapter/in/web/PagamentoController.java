package br.com.fiap.billing.pagamento.adapter.in.web;

import br.com.fiap.billing.pagamento.application.port.in.PagamentoDTO;
import br.com.fiap.billing.pagamento.application.port.in.PagamentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing/pagamentos")
@RequiredArgsConstructor
@Tag(name = "Pagamentos", description = "Gestão de pagamentos via Mercado Pago")
public class PagamentoController {

    private final PagamentoUseCase useCase;

    @PostMapping
    @Operation(summary = "Iniciar pagamento para uma OS (gera link Mercado Pago)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PagamentoDTO.Response> iniciarPagamento(
            @Valid @RequestBody PagamentoDTO.IniciarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCase.iniciarPagamento(request));
    }

    @GetMapping("/{osId}")
    @Operation(summary = "Consultar status do pagamento pelo ID da OS")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PagamentoDTO.Response> buscarPorOsId(@PathVariable String osId) {
        return ResponseEntity.ok(useCase.buscarPorOsId(osId));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Webhook do Mercado Pago — endpoint público")
    public ResponseEntity<Void> webhook(@RequestBody PagamentoDTO.WebhookRequest request) {
        useCase.processarWebhook(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{osId}/confirmar-teste")
    @Operation(summary = "Confirma pagamento para fins de demonstração (sem Mercado Pago)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PagamentoDTO.Response> confirmarTeste(@PathVariable String osId) {
        return ResponseEntity.ok(useCase.confirmarPagamentoTeste(osId));
    }
}
