# BDD do Billing

O cenário executável está em
`src/test/resources/features/billing-payment.feature`. Ele comprova que:

- um orçamento aprovado habilita a criação do checkout;
- repetições técnicas não criam outro pagamento;
- o webhook autenticado é confirmado consultando o provedor;
- o pagamento termina como `APPROVED`.

Os limites do OS Service e do Mercado Pago são simulados no teste do
repositório Billing. Para o BDD final do grupo, o mesmo cenário deve:

1. iniciar por uma OS real no OS Service;
2. receber os eventos de orçamento pelo RabbitMQ;
3. aprovar o orçamento pela API do Billing;
4. usar o sandbox real ou o stub HTTP controlado do Mercado Pago;
5. confirmar que o OS Service consumiu `billing.payment.approved.v1`;
6. verificar o estado final da OS sem acessar bancos de outros serviços.

O cenário cross-service permanece uma atividade conjunta porque depende dos
repositórios, contratos implantados e ambiente dos demais alunos.
