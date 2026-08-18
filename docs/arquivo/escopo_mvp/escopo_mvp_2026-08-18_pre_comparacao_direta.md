# Escopo técnico do MVP

## Papel deste documento

Este arquivo é a fonte canônica dos requisitos, regras do jogo, fórmulas,
modelo persistido, fluxos, restrições técnicas e critérios de aceite do
ForgeMaster Assistente.

`AGENTS.md` define como o Codex deve trabalhar; `MEMORY.md` registra apenas o
estado operacional e as pendências; `README.md` apresenta e orienta a navegação
do projeto. Esses arquivos podem apontar para esta especificação, mas não devem
manter cópias paralelas das definições abaixo.

## Objetivo

Ler localmente as telas de equipamento e dos inventários de pets e montarias
do Forge Master, simular a substituição da fonte equipada pelo candidato e
indicar a variação percentual do dano total no modo de arma ativo.

O aplicativo será de uso estritamente pessoal, distribuído somente como APK
local e não publicado em loja. A V1 considera exclusivamente dano; métricas de
sobrevivência podem ser exibidas como informação, mas não influenciam a
recomendação.

O app somente lê, calcula e recomenda. Ele não automatiza toques, venda ou
equipamento de itens no jogo.

O número de poder exibido pelo jogo é irrelevante e não deve ser lido, salvo ou
usado em qualquer cálculo.

## Aparelho-alvo

- Samsung Galaxy S24 Ultra.
- Android 16.
- One UI 8.5.
- A V1 suporta exclusivamente Android 16/API 36. Compatibilidade com outras
  versões do Android fica fora do MVP.

As permissões, dimensões de captura, recortes de OCR e comportamento da
sobreposição deverão ser validados prioritariamente nessa combinação.

## Fórmulas canônicas

Os fatores comuns aos dois modos são:

```text
(1 + Damage)
× (1 + Attack Speed)
× (1 + Double Chance efetiva)
× (1 + Critical Chance efetiva × Critical Damage)
```

O modo ativo acrescenta somente um multiplicador específico:

```text
Modo Melee:  × (1 + Melee Damage)
Modo Ranged: × (1 + Ranged Damage)
```

Os percentuais devem ser convertidos para decimais antes do cálculo. Por
exemplo, `20.8%` vira `0.208`.

Critical Chance e Double Chance têm teto de 100%:

```text
Critical Chance efetiva = min(Critical Chance total, 1)
Double Chance efetiva = min(Double Chance total, 1)
```

Os demais atributos não têm teto conhecido e devem entrar no cálculo com o
valor total reconhecido, sem limitação artificial.

O dano usado na comparação é o dano total, não apenas o multiplicador:

```text
dano_base_total = soma dos valores de Base Damage das fontes que o concedem
dano_total = dano_base_total × multiplicador_do_modo_ativo
delta = dano_total_novo / dano_total_atual - 1
```

Os valores abreviados e arredondados exibidos pelo jogo (`k`, `m`, `b`) são
aceitáveis para esse cálculo.

O app deve possuir um seletor manual `Melee`/`Ranged`, acessível na tela
principal e na sobreposição. O modo ativo deve continuar visível durante as
comparações e o último modo escolhido deve persistir entre reinicializações.
O tipo de arma não muda esse seletor automaticamente: inclusive ao comparar
ou equipar uma arma do outro tipo, a troca de `Melee` para `Ranged` ou
vice-versa continua sendo uma ação manual do usuário. O app calcula sempre
pelo modo selecionado, ignorando o bônus específico do modo inativo; o usuário
pode alternar o seletor para consultar o outro cenário.

Os incrementos de referência registrados na planilha são:

| Atributo | Incremento de referência |
|---|---:|
| Critical Chance | 12% |
| Critical Damage | 80% |
| Double Chance | 20% |
| Damage | 15% |
| Melee Damage | 50% |
| Ranged Damage | 15% |
| Attack Speed | 40% |

Lifesteal aparece no solver, mas não participa do dano. Block, Health Regen,
Skill Damage, Skill Cooldown e Health também não entram na pontuação da V1.
Melee Damage e Ranged Damage são mutuamente exclusivos no cálculo conforme o
modo manual selecionado.

## O que aproveitar do Excel

- Reaproveitar a fórmula-base da aba `melee`; o modo `Ranged` usa a mesma
  fórmula, substituindo Melee Damage por Ranged Damage. A aba `ranged` pode ser
  ignorada pelo app.
- Considerar Critical Damage básico de 20% e Critical Chance básico de zero.
- Não usar os valores da tabela `MAX` para avaliar itens reais; ela representa
  rolls máximos usados somente na otimização teórica.
- Não usar no app as quantidades escolhidas em `B30:B36`, a combinação ideal
  teórica ou as restrições do Solver.
- O valor correto da combinação teórica melee é `Double = 2`; o `1` registrado
  foi erro de digitação.
- `Lifesteal = 2` representa uma preferência teórica de pelo menos 40% de
  Lifesteal, mas não deve influenciar a recomendação de dano do app.
- A tabela amarela é apenas histórico de resultados para diferentes números de
  substats.
- O `#REF!` existente nos metadados do Solver pode ser ignorado.

## Modelo persistido da build

A build não deve ser salva apenas como uma linha de totais. Cada origem precisa
ter seu próprio registro para permitir atualizações parciais:

- 12 slots no total: 8 itens, 1 montaria e 3 pets ativos;
- 1 fonte agregada `Skills`, lida pelo banner de Base Damage e Base Health.

Itens, montaria e pets usam o mesmo modelo de atributos, persistência,
substituição e cálculo. Seus fluxos de captura são diferentes e devem ser
tratados separadamente: equipamentos forjados possuem uma tela de comparação;
montaria e pets mostram uma única fonte por vez em seus inventários.

Cada um dos 12 slots — itens, montaria e pets — pode ter zero, um ou dois
substats. Portanto, a build possui entre 0 e 24 substats. A ausência de um ou
dos dois substats é válida e não deve ser tratada como falha de OCR. `Skills`
não tem substats por definição. Os três ícones de habilidades equipadas não
devem ser calibrados individualmente; somente o banner agregado de todas as
skills é relevante.

Cada registro deve guardar, quando disponível:

- tipo e identificador da fonte;
- nome, raridade e nível;
- slot, quando for equipamento;
- stat principal de dano;
- stat principal de vida, embora não participe da decisão da V1;
- lista de substats reconhecidos;
- data da última leitura e confiança do OCR.

Na fonte `Skills`, guardar somente Base Damage, Base Health, data da leitura e
confiança do OCR.

Uma fonte pode pertencer a uma destas configurações de stats principais:

- dano e vida;
- somente dano;
- somente vida.

A ausência esperada de dano ou vida deve ser representada explicitamente no
registro e não pode ser confundida com falha de OCR.

A configuração é fixa para cada slot e pode ser usada para validar a leitura.
A única exceção conhecida é a arma:

- sempre deve apresentar dano principal;
- pode apresentar ou não vida principal.

Na arma, vida ausente é válida; dano ausente indica leitura incompleta.

### Mapa fixo de stats principais

| Fonte | Stats principais esperados |
|---|---|
| Cabeça | Vida |
| Torso | Vida |
| Luva | Dano |
| Colar | Dano |
| Anel | Dano |
| Arma | Dano ou dano + vida |
| Bota | Vida |
| Cinto | Vida |
| Montaria | Vida + dano |
| Pet 1 | Vida + dano |
| Pet 2 | Vida + dano |
| Pet 3 | Vida + dano |
| Skills, fonte agregada | Vida + dano |

Na tela de equipamentos, a montaria é o card verde retangular; os pets são os
três cards quadrados menores.

Os totais da build são sempre derivados da soma desses registros. Isso permite
substituir uma única fonte sem perder o restante da calibração.

## Calibração inicial

Na primeira utilização, o app deve conduzir uma sequência assistida:

1. Escanear individualmente os 8 itens, a montaria e os 3 pets ativos.
2. Escanear uma vez o banner agregado da página `Skills`, que mostra o Base
   Damage e o Base Health contribuídos por todas as skills.
3. Mostrar uma revisão dos valores reconhecidos em cada fonte.
4. Somar as fontes e comparar o resultado com os totais exibidos pelo jogo,
   quando esses totais estiverem disponíveis.
5. Salvar a build somente depois da confirmação do usuário.

O progresso deve ser persistido. Se a calibração for interrompida, ela poderá
continuar do próximo componente pendente.

A tela geral com `Total Damage`, `Total Health` e a lista rolável de atributos
serve para conferência dos totais calculados. Ela não é uma fonte adicional da
build e seus valores não podem ser somados novamente aos registros individuais.

## Recalibração parcial

O usuário deve poder selecionar qualquer combinação de fontes que saiba que
mudou, por exemplo:

- um item específico;
- o banner agregado de `Skills`, depois de qualquer melhoria de skill;
- um pet trocado ou melhorado;
- várias fontes escolhidas manualmente.

Somente as fontes selecionadas são escaneadas novamente. Antes de salvar, o
app mostra os valores antigos, os novos e o efeito nos totais. As demais fontes
permanecem intactas. Também deve existir uma opção separada de recalibração
completa.

## Fluxos de comparação

Para comparar atributos diferentes de forma correta, o app precisa dos totais
atuais da build, e não apenas dos valores visíveis no momento.

```text
build_nova = build_atual - fonte_equipada + fonte_candidata
delta = dano(build_nova) / dano(build_atual) - 1
```

### Equipamentos forjados

O jogo não possui inventário de equipamentos. Cada candidato aparece em um
pop-up de comparação com o equipamento atual e precisa ser vendido ou equipado
antes que o próximo candidato possa ser visto.

O fluxo é estritamente sequencial e deve funcionar para qualquer quantidade de
itens forjados:

1. O usuário abre um item no jogo.
2. A sobreposição reconhece o item equipado e o candidato.
3. O app mostra o dano antes, o dano depois, o delta percentual e as mudanças
   de stats.
4. O usuário vende o candidato ou confirma que o equipou.
5. Quando a troca é confirmada, o registro do slot é substituído e a build
   salva passa a ser a nova base das comparações seguintes.

O jogo só permite visualizar o próximo candidato depois de vender ou equipar o
atual. Por isso, o app não precisa manter histórico de candidatos. Um candidato
vendido é descartado; apenas uma troca confirmada altera a build persistida.

A V1 não toca no botão `Equip` do jogo. Depois de equipar no jogo, o usuário
toca `Equipei` na sobreposição. Essa confirmação substitui o registro do slot
pelo candidato e recalcula a build salva.

Antes de confirmar `Equipei`, o app deve manter uma cópia do registro anterior
do slot. A ação `Desfazer última troca` restaura essa cópia e os totais da build.
Existe apenas um nível de desfazer: uma nova troca confirmada substitui a cópia
anterior. Isso não constitui histórico de candidatos.

### Montaria

Montarias ficam em um inventário próprio. A tela mostra apenas a montaria
selecionada, sem comparação lado a lado. O app deve:

1. ler a montaria candidata aberta;
2. compará-la com a única montaria equipada registrada na build;
3. mostrar o dano antes, o dano depois, o delta e todos os atributos alterados;
4. atualizar o slot de montaria somente depois da confirmação do usuário.

### Pets

Pets ficam em um inventário próprio e também não possuem tela de comparação. A
build contém três pets ativos independentes. Para cada pet candidato aberto, o
app deve simular três substituições:

```text
delta_pet_1 = dano(build_atual - pet_1 + candidato) / dano(build_atual) - 1
delta_pet_2 = dano(build_atual - pet_2 + candidato) / dano(build_atual) - 1
delta_pet_3 = dano(build_atual - pet_3 + candidato) / dano(build_atual) - 1
```

A sobreposição deve mostrar o resultado contra cada pet atual e destacar a
substituição que produz o maior dano total. O candidato é melhor se pelo menos
uma das três substituições tiver delta positivo; a recomendação aponta o pet
cuja troca produz o maior delta.

Como o comportamento de `Equip` com os três slots ocupados não garante qual
pet será substituído, a V1 adota o fluxo seguro confirmado:

1. o app recomenda qual pet remover, mantendo disponíveis os três cenários;
2. o usuário abre esse pet no jogo e toca `Remove`;
3. o usuário abre o candidato e toca `Equip`;
4. somente depois das duas ações, confirma a troca na sobreposição.

O app deve substituir atomicamente o pet removido pelo candidato na build
salva. O estado intermediário com um slot vazio não deve ser persistido. Se o
usuário optar por remover outro pet, deve selecionar esse slot na confirmação;
os outros dois registros permanecem intactos.

Os três slots devem possuir identificadores próprios, como `Pet 1`, `Pet 2` e
`Pet 3`, definidos durante a calibração. Nome e nível não identificam o slot,
pois podem existir dois pets equipados com o mesmo nome e nível, mas com
atributos diferentes.

## Stats principais

O Base Damage de todos os slots aplicáveis e do banner de `Skills` participa do
dano total. Base Health é armazenado para manter a build completa, mas não
afeta a recomendação de dano da V1.

## Captura e OCR

1. Solicitar MediaProjection ao iniciar uma sessão de leitura.
2. Solicitar permissão de sobreposição uma única vez.
3. Identificar o tipo de painel antes de escolher os recortes:
   - detalhe individual de um equipamento já equipado, para calibração;
   - comparação da forja, com regiões de equipado e candidato;
   - detalhe individual de montaria ou pet, equipado ou candidato;
   - banner agregado de `Skills`.
4. Reconhecer nome, nível, atributo principal e os substats exibidos pela
   fonte.
   A leitura deve aceitar fontes com os dois stats principais, somente dano ou
   somente vida.
   Para `Skills`, reconhecer apenas o banner agregado de Base Damage e Base
   Health; não procurar substats.
   Nos equipamentos, identificar o slot automaticamente por termos exatos do
   nome do item, sem exigir que o usuário selecione a peça: `Suit` para torso,
   `Crown` para cabeça, `Grip`, `Glove`, `Gloves` ou `Impulse` para luva,
   `Necklace` para colar, `Ring` para
   anel, `Sword` para arma, `Greaves` ou `Feet` para bota e `Belt` para cinto.
   Um slot pode receber outros nomes confirmados pelo usuário futuramente. Não
   criar aliases a partir de erros do OCR nem usar semelhança aproximada para
   adivinhar o slot.
   Na comparação da forja, equipado e candidato pertencem ao mesmo slot. Basta
   que um dos dois nomes identifique esse slot; se ambos identificarem slots,
   eles devem concordar. Um nome ainda não catalogado não invalida a comparação
   quando o outro nome identifica o slot com segurança.
   Quando isso ocorrer, associar automaticamente o nome novo ao slot conhecido
   em um catálogo JSON local. O catálogo guarda somente o nome normalizado e o
   slot, não os atributos nem um histórico do candidato. Uma associação
   existente para outro slot não pode ser sobrescrita silenciosamente. Termos
   explícitos do nome, como `Ring` ou `Sword`, prevalecem sobre o catálogo.
   Montarias e pets devem ser diferenciados pelo painel aberto, incluindo os
   textos `Mounts` e `Pets` visíveis ao fundo.
5. Normalizar variações como `Critical Chance`, `Critical Damage`,
   `Lifesteal`, `Double Chance`, `Damage`, `Melee Damage`, `Ranged Damage`,
   `Attack Speed` e os demais substats exibidos pelo jogo.
6. Exibir os valores reconhecidos em campos editáveis antes da decisão. Uma
   correção manual deve recalcular imediatamente a comparação, e os valores
   corrigidos são os usados em `Equipei`.
7. Se faltar algum valor, se o sinal estiver ambíguo ou se a confiança estiver
   abaixo do limite configurado, marcar a leitura para revisão manual. Enquanto
   a revisão não estiver completa, o resultado permanece `INCONCLUSIVO`.
   Fora da comparação, se o nome não produzir um slot exato, a fonte não pode
   ser salva no rascunho nem na build. Na comparação, bloquear somente quando
   os nomes identificarem slots diferentes; um nome novo pode herdar o slot do
   outro item e ser aprendido conforme a regra acima.

## Saída na sobreposição

Exemplo:

```text
EQUIPAR  +3,7% dano melee
+49% Melee Damage supera a perda de 16,4% Lifesteal.
OCR: 96%  |  Toque para ver os valores lidos
```

Ao expandir a sobreposição, o usuário deve ver uma comparação auditável. O
número de poder não deve aparecer:

| Métrica | Build atual | Após equipar | Alteração |
|---|---:|---:|---:|
| Dano total | 11,6m | 12,0m | +3,4% |
| Melee Damage | valor atual | novo valor | diferença em p.p. |
| Critical Chance | valor atual | novo valor | diferença em p.p. |

A lista deve incluir todos os stats principais e substats alterados pela troca,
mesmo quando não participarem da fórmula de dano. Stats e substats que
permanecerem iguais devem ser omitidos. Métricas de sobrevivência e o dano do
modo inativo aparecem como informação, marcados como ignorados pela decisão.

## Interação no estilo Poke Genie

- O usuário inicia a sessão de sobreposição no app e retorna ao Forge Master.
- Bolha pequena, móvel e recolhível sobre o jogo.
- Um toque simples na bolha captura um único quadro da tela e inicia a leitura.
  A V1 não faz captura nem análise contínua em segundo plano.
- Um toque longo na bolha abre o atalho de calibração e recalibração, inclusive
  dos recortes usados na captura.
- Cartão expansível com leitura, cálculo e detalhes dos stats.
- Ações rápidas para descartar a leitura, corrigir um valor reconhecido e
  registrar pelo botão `Equipei` que o item foi equipado no jogo.
- Ação `Desfazer última troca`, disponível enquanto existir a cópia da última
  troca confirmada.
- Acesso à calibração e à recalibração parcial sem encerrar o jogo.
- Seletor manual `Melee`/`Ranged` acessível sem sair do jogo.
- Nenhum toque automático no jogo.

O texto explicativo deve mencionar somente as mudanças reconhecidas. A decisão
final segue o delta exato do dano total:

- delta positivo: melhor;
- delta negativo: pior;
- delta zero: `INCONCLUSIVO`, com o motivo `dano equivalente`;
- OCR incompleto ou inseguro: `INCONCLUSIVO`.

## Limites conhecidos

- O Android exige confirmação do usuário para iniciar a captura de tela; uma
  permissão silenciosa permanente não é garantida.
- Mudanças de resolução, idioma, escala da interface ou layout do jogo podem
  exigir novos recortes e testes de OCR.
- Itens cuja vantagem dependa de sobrevivência podem receber recomendação
  diferente da preferência real do usuário, por decisão explícita de escopo.
- A V1 não toca em `Sell` nem `Equip` e não controla o jogo.

## Critérios de aceite do protótipo

- Ler corretamente todas as capturas indexadas em
  `20_referencias/telas/README.md`.
- Concluir e persistir a calibração dos 8 itens, da montaria, dos 3 pets e do
  banner agregado de `Skills`.
- Recalibrar uma fonte isolada sem modificar as demais.
- Reproduzir a fórmula do Excel em testes unitários.
- Aplicar o teto de 100% somente a Critical Chance e Double Chance.
- Verificar em testes que Melee Damage é ignorado no modo `Ranged` e Ranged
  Damage é ignorado no modo `Melee`.
- Nunca alternar automaticamente o modo ao comparar ou equipar outra categoria
  de arma; a mudança deve depender do seletor manual.
- Mostrar os valores reconhecidos em campos editáveis antes de permitir uma
  decisão confiável e recalcular após uma correção.
- Calcular a substituição sem perder substats não relacionados ao item.
- Atualizar a build salva depois de uma troca confirmada.
- Desfazer a última troca confirmada sem manter histórico anterior.
- Processar uma sequência de itens sem reiniciar a sessão de captura.
- Não manter histórico de candidatos vendidos.
- Aceitar de zero a dois substats em qualquer um dos 12 slots sem transformar
  uma ausência válida em erro de OCR.
- Comparar uma montaria candidata com a montaria salva, mesmo sem comparação
  lado a lado no jogo.
- Comparar um pet candidato separadamente com os três pets ativos, destacar a
  melhor substituição e atualizar somente o slot confirmado.
- Registrar a troca de pet de forma atômica, sem persistir o intervalo entre
  `Remove` e `Equip` como parte da build.
- Executar somente uma captura e uma análise por toque simples na bolha, sem
  varredura contínua da tela.
- Disponibilizar calibração e recalibração pelo toque longo na bolha.
- Exibir apenas os stats e substats alterados pela troca.
- Funcionar sem internet e sem registrar imagens fora do aparelho.
