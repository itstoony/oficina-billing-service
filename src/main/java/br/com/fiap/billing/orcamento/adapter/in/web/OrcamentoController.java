package br.com.fiap.billing.orcamento.adapter.in.web;

import br.com.fiap.billing.orcamento.application.port.in.OrcamentoDTO;
import br.com.fiap.billing.orcamento.application.port.in.OrcamentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing/orcamentos")
@RequiredArgsConstructor
@Tag(name = "Orçamentos", description = "Gestão de orçamentos de OS")
@SecurityRequirement(name = "bearerAuth")
public class OrcamentoController {

    private final OrcamentoUseCase useCase;

    @PostMapping
    @Operation(summary = "Gerar orçamento para uma OS")
    public ResponseEntity<OrcamentoDTO.Response> gerar(@Valid @RequestBody OrcamentoDTO.GerarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(useCase.gerar(request));
    }

    @GetMapping("/{osId}")
    @Operation(summary = "Buscar orçamento pelo ID da OS")
    public ResponseEntity<OrcamentoDTO.Response> buscarPorOsId(@PathVariable String osId) {
        return ResponseEntity.ok(useCase.buscarPorOsId(osId));
    }
}
