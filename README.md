# oficina-billing-service

Microsserviço de Billing — FIAP Pós-Tech Software Architecture Fase 4.

## Responsabilidade

Geração de orçamentos, integração com Mercado Pago e controle de pagamentos.

## Stack

- Java 17 + Spring Boot 3.5.3
- MongoDB (banco não relacional)
- RabbitMQ (mensageria assíncrona)
- Mercado Pago SDK
- Arquitetura Hexagonal (Ports & Adapters) + DDD

## Porta

`8082`

## Principais endpoints

| Método | Path | Descrição |
|--------|------|-----------|
| POST | `/api/billing/orcamentos` | Gerar orçamento |
| GET | `/api/billing/orcamentos/{osId}` | Consultar orçamento |
| POST | `/api/billing/pagamentos` | Iniciar pagamento (Mercado Pago) |
| GET | `/api/billing/pagamentos/{osId}` | Status do pagamento |
| POST | `/api/billing/pagamentos/webhook` | Webhook Mercado Pago (público) |

## Eventos publicados

| Routing Key | Quando |
|-------------|--------|
| `orcamento.gerado` | Orçamento criado/atualizado |
| `pagamento.confirmado` | Pagamento aprovado pelo Mercado Pago |
| `pagamento.recusado` | Pagamento recusado |

## Eventos consumidos

| Routing Key | Ação |
|-------------|------|
| `os.aberta` | Cria orçamento inicial |
| `orcamento.solicitado` | Atualiza orçamento com itens detalhados |

## Executar localmente

```bash
docker-compose up -d
./mvnw spring-boot:run
```

## Testes

```bash
./mvnw test
```
