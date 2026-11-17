# language: pt
Funcionalidade: Falha e compensação da Saga de orçamento
  Como OS Service
  Quero que o Billing publique falhas e compense o orçamento localmente
  Para que a OS nunca avance sobre um orçamento inconsistente

  Cenário: Falha na solicitação de orçamento publica billing.quote.failed.v1
    Dado uma solicitação de orçamento inválida para a OS "OS-BDD-FAIL"
    Quando o QuoteSagaHandler processa a solicitação
    Então o evento publicado é QUOTE_FAILED com código INVALID_QUOTE_DATA

  Cenário: Cancelamento da OS compensa o orçamento local
    Dado um orçamento pendente de aprovação para a OS "OS-BDD-CANCEL"
    Quando o QuoteSagaHandler processa o cancelamento da OS
    Então o orçamento fica com status CANCELLED
    E o evento publicado é QUOTE_CANCELLED
