# language: pt
Funcionalidade: Pagamento de um orçamento aprovado
  Como OS Service
  Quero receber o resultado financeiro publicado pelo Billing
  Para iniciar a execução somente depois da confirmação do pagamento

  Cenário: Confirmar um pagamento sem duplicar a cobrança
    Dado um orçamento aprovado no valor de "150.00" BRL
    Quando o Billing cria duas vezes o checkout para o mesmo orçamento
    E o Mercado Pago notifica um pagamento aprovado e autenticado
    Então existe somente um pagamento local
    E o pagamento fica com status APPROVED
