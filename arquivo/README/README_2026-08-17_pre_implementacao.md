# ForgeMaster Assistente

Projeto de um futuro aplicativo Android pessoal, inspirado no fluxo do Poke
Genie, para ler a tela do Forge Master e indicar se um item novo melhora o dano
da build atual.

## Estado

- Viabilidade técnica confirmada.
- Solver original e capturas de referência preservados em `20_referencias/`.
- Regra da V1 definida: avaliar a variação do dano total, com modo manual
  `Melee` ou `Ranged`.
- Implementação pausada por decisão do usuário; não há app, projeto Gradle ou
  APK criado.
- Uma implementação futura só deve começar após novo pedido explícito.

## Regra funcional da V1

- A build possui 12 slots: 8 itens, 1 montaria e 3 pets. Cada slot pode ter de
  0 a 2 substats, totalizando entre 0 e 24. Todos usam a mesma regra de cálculo,
  mas equipamentos, montaria e pets têm fluxos de captura diferentes.
- Além dos 12 slots, existe uma fonte agregada `Skills`, lida pelo banner de
  Base Damage e Base Health; skills não têm substats.
- Dependendo do slot, a fonte pode conceder vida e dano principais, somente
  dano ou somente vida. Apenas o dano participa do cálculo da V1.
- Essa configuração é fixa por slot, exceto na arma: ela sempre concede dano e
  pode conceder também vida.
- A build deve ser salva por fonte individual para permitir recalibração
  parcial e substituição de apenas um componente.
- A decisão deve usar a fórmula de dano generalizada a partir do solver, não
  uma soma subjetiva de pesos.
- Um seletor manual define o tipo da arma: no modo `Melee`, Ranged Damage é
  ignorado; no modo `Ranged`, Melee Damage é ignorado.
- A combinação ideal teórica, os valores `MAX` e o mínimo de Lifesteal do
  Solver não influenciam a recomendação do app.
- Vida, block, regen e demais atributos de sobrevivência não influenciam a
  recomendação da V1.
- O app deve mostrar o impacto estimado da troca em percentual e um resultado:
  `EQUIPAR`, `VENDER` ou `INCONCLUSIVO`.
- Leituras com baixa confiança de OCR devem ser classificadas como
  `INCONCLUSIVO`, sem tentar adivinhar valores.

## Fluxo previsto

1. O usuário inicia a sobreposição, concede as permissões do Android e retorna
   ao Forge Master.
2. Na primeira calibração, o app lê individualmente os 8 itens, a montaria e os
   3 pets, além do banner agregado de `Skills`.
3. Com a tela desejada aberta, o usuário toca na bolha para fazer uma única
   captura. Na forja, o app lê o equipamento atual e o candidato mostrados
   juntos. Nos inventários de montaria e pets, ele lê somente a fonte aberta e
   usa a build salva como base da comparação.
4. O app simula a substituição na build atual e recalcula a fórmula de dano;
   um pet candidato é comparado separadamente com os três pets ativos.
5. Uma sobreposição móvel informa o delta do dano total e a alteração de cada
   stat envolvido.
6. Para trocar um pet, o usuário remove primeiro o pet escolhido e depois
   equipa o candidato; a sobreposição registra as duas ações como uma única
   substituição na build.
7. Depois de tocar `Equip` no jogo, o usuário confirma a troca na sobreposição
   para atualizar imediatamente o slot e a build salva.
8. O usuário pode recalibrar somente as fontes que souber que mudaram, sem
   repetir toda a calibração.

## Estrutura

```text
90_forgemaster_assistente/
|-- README.md
|-- AGENTS.md
|-- MEMORY.md
|-- 10_app_android/
|   `-- README.md
|-- 20_referencias/
|   |-- ForgeMaster.xlsx
|   `-- telas/
|       |-- README.md
|       `-- 15 capturas de referência
`-- docs/
    `-- escopo_mvp.md
```

## Restrições do projeto

- Uso estritamente pessoal; não publicar em loja.
- Processamento local, sem enviar capturas ou dados para servidores.
- Não automatizar toques, venda ou equipamento de itens.
- Preservar o solver e as capturas originais como fontes de referência.
