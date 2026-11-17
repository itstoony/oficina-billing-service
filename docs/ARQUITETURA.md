# Documentação de Arquitetura — Billing Service
## FIAP Pós-Tech Software Architecture — Tech Challenge Fase 4

---

## Sumário

1. [Visão Geral do Serviço](#1-visão-geral-do-serviço)
2. [Arquitetura da Fase 4 — Microsserviços](#2-arquitetura-da-fase-4--microsserviços)
3. [Diagrama de Componentes do Serviço](#3-diagrama-de-componentes-do-serviço)
4. [Diagrama de Sequência — Saga Happy Path](#4-diagrama-de-sequência--saga-happy-path)
5. [Diagrama de Sequência — Saga Rollback](#5-diagrama-de-sequência--saga-rollback)
6. [Modelo de Dados (MongoDB)](#6-modelo-de-dados-mongodb)
7. [Arquitetura Hexagonal](#7-arquitetura-hexagonal)
8. [Integração Mercado Pago](#8-integração-mercado-pago)
9. [Eventos Publicados e Consumidos](#9-eventos-publicados-e-consumidos)
10. [Justificativa do Banco de Dados](#10-justificativa-do-banco-de-dados)
11. [ADRs](#11-adrs)

---

## 1. Visão Geral do Serviço

O **Billing Service** é responsável pela geração de orçamentos e processamento de pagamentos das Ordens de Serviço. Integra com o Mercado Pago para geração de links de pagamento e processa callbacks via webhook.

| Atributo | Valor |
|---|---|
| Linguagem | Java 17 |
| Framework | Spring Boot 3.5.3 |
| Banco de Dados | MongoDB |
| Mensageria | RabbitMQ (produtor e consumidor) |
| Porta | 8082 |
| Integração externa | Mercado Pago SDK |

### Responsabilidades

- Geração de orçamentos a partir de eventos `os.aberta`
- Criação de preferências de pagamento no Mercado Pago
- Processamento de webhooks de confirmação de pagamento
- Publicação de `pagamento.confirmado` para liberar execução da OS

---

## 2. Arquitetura da Fase 4 — Microsserviços

Diagrama completo da comunicação entre os 3 microsserviços via RabbitMQ (Exchange: `oficina.events`).

```mermaid
graph TB
    subgraph Cliente["Atores Externos"]
        ADM[Admin / Atendente]
        CLI[Cliente]
        MP[Mercado Pago\nWebhook]
    end

    subgraph RMQ["RabbitMQ — oficina.events (Topic Exchange)"]
        direction LR
        E1([os.aberta])
        E2([os.aprovada])
        E3([os.cancelada])
        E4([orcamento.gerado])
        E5([pagamento.confirmado])
        E6([pagamento.recusado])
        E7([estoque.reservado])
        E8([estoque.indisponivel])
        E9([execucao.finalizada])
    end

    subgraph OS["OS Service — :8080\nPostgreSQL"]
        OS_API[REST API]
        OS_CON[Event Consumer]
        OS_PUB[Event Publisher]
    end

    subgraph BILLING["Billing Service — :8082\nMongoDB"]
        B_API[REST API]
        B_CON[Event Consumer]
        B_PUB[Event Publisher]
    end

    subgraph EXEC["Execucao Service — :8081\nPostgreSQL"]
        EX_API[REST API]
        EX_CON[Event Consumer]
        EX_PUB[Event Publisher]
    end

    ADM -->|JWT Bearer| OS_API
    CLI -->|endpoint público| OS_API
    MP -->|webhook| B_API
    ADM -->|JWT Bearer| B_API

    OS_PUB -->|publica| E1
    OS_PUB -->|publica| E2
    OS_PUB -->|publica| E3

    E1 -->|consome| B_CON
    E2 -->|consome| EX_CON
    E3 -->|consome| EX_CON

    B_PUB -->|publica| E4
    B_PUB -->|publica| E5
    B_PUB -->|publica| E6

    E4 -->|consome| OS_CON
    E5 -->|consome| OS_CON
    E5 -->|consome| EX_CON
    E6 -->|consome| OS_CON

    EX_PUB -->|publica| E7
    EX_PUB -->|publica| E8
    EX_PUB -->|publica| E9

    E7 -->|consome| OS_CON
    E8 -->|consome| OS_CON
    E9 -->|consome| OS_CON
```

### Fluxo do Saga Pattern (Coreografado)

```mermaid
flowchart LR
    A([OS Aberta]) -->|os.aberta| B([Billing\ngera orçamento])
    B -->|orcamento.gerado| C([OS: AGUARDANDO\nAPROVACAO])
    C -->|cliente aprova| D([OS: APROVADO\nos.aprovada])
    D -->|os.aprovada| E{Execucao\nverifica estoque}
    E -->|estoque ok\nestoque.reservado| F([Billing\nprocessa pagamento])
    E -->|sem estoque\nestoque.indisponivel| Z([OS: CANCELADA\nrollback])
    F -->|pagamento.confirmado| G([OS: EM_EXECUCAO])
    F -->|pagamento.recusado| C
    G -->|execucao.finalizada| H([OS: FINALIZADA])
    H -->|admin entrega| I([OS: ENTREGUE])

    style Z fill:#ff6b6b,color:#fff
    style I fill:#51cf66,color:#fff
    style F fill:#339af0,color:#fff
    style B fill:#339af0,color:#fff
```

---

## 3. Diagrama de Componentes do Serviço

```mermaid
graph TB
    subgraph EKS["AWS EKS — oficina-cluster"]
        subgraph B_SVC["Billing Service Pod"]
            CTRL["Controllers\n(REST Adapters)"]
            SVC["Application Services\nOrcamentoService\nPagamentoService"]
            DOM["Domain Model\nOrcamento\nPagamento"]
            REPO["MongoDB Adapters\n(Spring Data MongoDB)"]
            MSG_IN["Event Consumer\n(RabbitMQ Listener)"]
            MSG_OUT["Event Publisher\n(RabbitMQ)"]
            MP_ADAPTER["Mercado Pago Adapter\n(MP SDK)"]
        end

        subgraph DB["billing-db Pod"]
            MONGO["MongoDB\nbilling_service"]
        end

        LB["LoadBalancer\n:8082"]
    end

    subgraph RMQ["RabbitMQ Pod"]
        QUEUE["billing.eventos.queue"]
    end

    subgraph MP["Mercado Pago"]
        MP_API["Checkout API"]
        MP_WH["Webhook Callback"]
    end

    LB --> CTRL
    MP_WH -->|POST /webhook| CTRL
    CTRL --> SVC
    SVC --> DOM
    SVC --> REPO
    SVC --> MSG_OUT
    SVC --> MP_ADAPTER
    REPO --> MONGO
    MSG_IN --> SVC
    QUEUE --> MSG_IN
    MSG_OUT --> QUEUE
    MP_ADAPTER --> MP_API
```

---

## 4. Diagrama de Sequência — Saga Happy Path

```mermaid
sequenceDiagram
    actor Admin
    actor Cliente
    participant OS as OS Service
    participant RMQ as RabbitMQ
    participant Billing as Billing Service
    participant MP as Mercado Pago
    participant Exec as Execucao Service

    Admin->>OS: POST /api/admin/ordens
    OS->>RMQ: publica os.aberta
    RMQ->>Billing: consome os.aberta
    Billing->>Billing: Cria orçamento (MongoDB)\nstatus: PENDENTE
    Billing->>RMQ: publica orcamento.gerado
    RMQ->>OS: consome orcamento.gerado
    OS->>OS: Atualiza valorTotal

    Admin->>OS: Envia orçamento → AGUARDANDO_APROVACAO
    Cliente->>OS: Aprova OS
    OS->>RMQ: publica os.aprovada (após commit)

    RMQ->>Exec: consome os.aprovada → reserva estoque
    Exec->>RMQ: publica estoque.reservado

    Admin->>Billing: POST /api/billing/pagamentos
    Billing->>MP: Cria preferência (Checkout API)
    MP-->>Billing: checkoutUrl
    Billing-->>Admin: 201 {checkoutUrl, status: PENDENTE}

    note over Admin,MP: Para demonstração: endpoint confirmar-teste
    Admin->>Billing: POST /api/billing/pagamentos/{osId}/confirmar-teste
    Billing->>Billing: status → APROVADO
    Billing->>RMQ: publica pagamento.confirmado
    RMQ->>OS: consome → EM_EXECUCAO
    RMQ->>Exec: consome → entra na fila

    Exec->>RMQ: publica execucao.finalizada
    RMQ->>OS: consome → FINALIZADA
```

---

## 5. Diagrama de Sequência — Saga Rollback

```mermaid
sequenceDiagram
    actor Admin
    actor Cliente
    participant OS as OS Service
    participant RMQ as RabbitMQ
    participant Exec as Execucao Service

    Admin->>OS: Abre OS com peça sem estoque
    OS->>RMQ: publica os.aberta

    Admin->>OS: Envia orçamento → AGUARDANDO_APROVACAO
    Cliente->>OS: Aprova OS
    OS->>RMQ: publica os.aprovada (após commit)

    RMQ->>Exec: consome os.aprovada
    Exec->>Exec: Verifica estoque → insuficiente
    Exec->>RMQ: publica estoque.indisponivel

    RMQ->>OS: consome estoque.indisponivel
    OS->>OS: status → CANCELADA
    note over OS: Saga compensado. Orçamento não prossegue.
```

---

## 6. Modelo de Dados (MongoDB)

```mermaid
graph TD
    subgraph Col1["Coleção: orcamentos"]
        O1[osId: String]
        O2[numeroOs: String]
        O3[clienteEmail: String]
        O4["itensServico: Array\n  - descricao, quantidade, valorUnitario"]
        O5["itensPeca: Array\n  - descricao, quantidade, valorUnitario"]
        O6[valorTotal: Decimal]
        O7["status: Enum\nPENDENTE | APROVADO | CANCELADO"]
        O8[dataCriacao: DateTime]
        O9[dataAtualizacao: DateTime]
    end

    subgraph Col2["Coleção: pagamentos"]
        P1[osId: String]
        P2[externalId: String\nID do Mercado Pago]
        P3[checkoutUrl: String]
        P4[valorPago: Decimal]
        P5["status: Enum\nPENDENTE | APROVADO | RECUSADO | CANCELADO"]
        P6[dataCriacao: DateTime]
        P7[dataPagamento: DateTime]
    end

    O1 -.->|referência lógica| P1
```

**Nota:** Não há FK entre coleções. A correlação é feita via `osId` (UUID da OS), que é a chave de ligação com o OS Service.

---

## 7. Arquitetura Hexagonal

```mermaid
graph LR
    subgraph Adapters_In["Adapters — Entrada"]
        REST["REST Controllers\n(Spring MVC)"]
        MSG_IN["Event Consumer\n(RabbitMQ Listener)"]
        WH["Webhook Handler\n(Mercado Pago)"]
    end

    subgraph Application["Application Core"]
        subgraph Ports_In["Ports — Entrada"]
            UC_O["OrcamentoUseCase"]
            UC_P["PagamentoUseCase"]
        end
        subgraph Domain["Domain"]
            ENT_O["Orcamento"]
            ENT_P["Pagamento"]
            VO["StatusOrcamento\nStatusPagamento"]
        end
        subgraph Ports_Out["Ports — Saída"]
            REPO_O["OrcamentoRepositoryPort"]
            REPO_P["PagamentoRepositoryPort"]
            EVENT_P["EventoPublicadorPort"]
            MP_P["MercadoPagoPort"]
        end
        SVC_O["OrcamentoService"]
        SVC_P["PagamentoService"]
    end

    subgraph Adapters_Out["Adapters — Saída"]
        MONGO_O["MongoDB Adapter\n(Orcamentos)"]
        MONGO_P["MongoDB Adapter\n(Pagamentos)"]
        RMQ_OUT["RabbitMQ Publisher"]
        MP_AD["Mercado Pago Adapter\n(MP SDK)"]
    end

    REST --> UC_O
    REST --> UC_P
    MSG_IN --> UC_O
    WH --> UC_P
    UC_O --> SVC_O
    UC_P --> SVC_P
    SVC_O --> ENT_O
    SVC_P --> ENT_P
    SVC_O --> REPO_O
    SVC_P --> REPO_P
    SVC_P --> MP_P
    SVC_O --> EVENT_P
    SVC_P --> EVENT_P
    REPO_O --> MONGO_O
    REPO_P --> MONGO_P
    EVENT_P --> RMQ_OUT
    MP_P --> MP_AD
```

---

## 8. Integração Mercado Pago

```mermaid
sequenceDiagram
    participant Admin
    participant Billing as Billing Service
    participant MP as Mercado Pago API

    Admin->>Billing: POST /api/billing/pagamentos\n{osId}
    Billing->>MP: POST /checkout/preferences\n{items, payer, notification_url}
    MP-->>Billing: {id, init_point (checkoutUrl)}
    Billing->>Billing: Salva pagamento (status: PENDENTE)\ncheckoutUrl = init_point
    Billing-->>Admin: 201 {checkoutUrl, status: PENDENTE}

    note over Admin,MP: Fluxo real de pagamento
    Admin->>MP: Acessa checkoutUrl (browser)
    MP->>MP: Cliente insere dados do cartão
    MP->>Billing: POST /api/billing/pagamentos/webhook\n{type: payment, data.id: extId}
    Billing->>MP: GET /v1/payments/{extId}
    MP-->>Billing: {status: approved, transaction_amount}
    Billing->>Billing: Atualiza pagamento → APROVADO
    Billing->>Billing: publica pagamento.confirmado

    note over Admin,Billing: Endpoint para demonstração (sem conta de teste)
    Admin->>Billing: POST /api/billing/pagamentos/{osId}/confirmar-teste
    Billing->>Billing: Atualiza pagamento → APROVADO\nexternalId = TESTE-{osId}
    Billing->>Billing: publica pagamento.confirmado
```

**Nota sobre o endpoint `confirmar-teste`:** O Mercado Pago exige contas de teste separadas para comprador e vendedor. Como o ambiente de desenvolvimento usa a conta produtiva do desenvolvedor, foi criado este endpoint auxiliar exclusivamente para demonstração acadêmica, que simula a confirmação sem passar pelo fluxo real de pagamento.

---

## 9. Eventos Publicados e Consumidos

### Publica

| Routing Key | Trigger | Payload principal |
|---|---|---|
| `orcamento.gerado` | Orçamento criado/atualizado | osId, numeroOs, valorTotal |
| `pagamento.confirmado` | Webhook aprovado ou confirmar-teste | osId, externalId, valorPago |
| `pagamento.recusado` | Webhook recusado | osId, motivo |

### Consome

| Routing Key | Origem | Ação |
|---|---|---|
| `os.aberta` | OS Service | Cria orçamento inicial no MongoDB |

---

## 10. Justificativa do Banco de Dados

**MongoDB** foi escolhido para o Billing Service pelos seguintes motivos:

- Orçamentos têm estrutura variável: arrays de itens de serviço e peças com diferentes atributos
- Dados de pagamento incluem metadados do Mercado Pago com estrutura flexível
- Sem necessidade de JOINs — cada documento é auto-contido
- Alta velocidade de leitura/escrita para processamento de webhooks
- Atende ao requisito obrigatório de **banco NoSQL** na arquitetura

---

## 11. ADRs

### ADR-001: MongoDB como Banco de Dados

**Status:** Aceito

**Contexto:** O Billing Service precisa armazenar orçamentos com estrutura de itens variável e dados de pagamento do Mercado Pago.

**Decisão:** Usar MongoDB como banco de dados principal do Billing Service.

**Justificativa:**
- Documentos JSON se alinham naturalmente com a estrutura de orçamentos e payloads do Mercado Pago
- Flexibilidade de schema para campos opcionais do webhook
- Atende ao requisito de banco NoSQL da Fase 4

---

### ADR-002: Endpoint `confirmar-teste` para Demonstração

**Status:** Aceito

**Contexto:** A integração com Mercado Pago em ambiente de testes requer contas de teste separadas para comprador e vendedor. A API de criação de contas de teste não está disponível com token de aplicação produtiva.

**Decisão:** Implementar endpoint `POST /api/billing/pagamentos/{osId}/confirmar-teste` que confirma o pagamento sem passar pelo Mercado Pago, publicando diretamente o evento `pagamento.confirmado`.

**Consequências:**
- Permite demonstração do fluxo completo da Saga sem dependência de contas de teste
- O link do Mercado Pago é gerado normalmente e pode ser exibido como evidência da integração
- O endpoint é protegido por autenticação JWT (admin apenas)
