# ForgeMaster Assistente

Aplicativo Android experimental que usa OCR local para ler painéis do jogo
Forge Master, reconstruir a build ativa e comparar candidatos de equipamento,
montaria e pet. A experiência é inspirada no fluxo do Poke Genie: uma bolha
fica sobre o jogo, captura uma única imagem quando tocada e apresenta a análise
sem executar ações no jogo.

> Projeto pessoal, independente e não oficial. Não possui vínculo, aprovação
> ou afiliação com os desenvolvedores ou detentores da marca Forge Master.

## Objetivo e estado atual

O MVP foi desenvolvido para um Galaxy S24 Ultra com Android 16/API 36. Ele já
implementa:

- calibração persistente de 8 equipamentos, 1 montaria, 3 pets e o agregado de
  Skills;
- captura sob demanda por `MediaProjection`, sem análise contínua da tela;
- OCR inteiramente local com ML Kit Text Recognition bundled;
- comparação de dano nos modos Melee e Ranged;
- reconhecimento e aprendizado local de nomes de equipamentos por slot;
- comparação do candidato contra os três pets ativos;
- normalização de nível para pets e montarias;
- correção manual, descarte, confirmação de troca e um nível de desfazer;
- persistência local com Preferences DataStore.

O app não toca automaticamente em `Sell`, `Equip` ou `Remove`. O jogador toma
essas decisões no jogo e apenas confirma o resultado na sobreposição.

## Fluxo de uma captura

1. O usuário concede permissão de sobreposição e inicia uma sessão de captura.
2. Ao tocar na bolha, ela é ocultada por um instante.
3. O serviço captura um único frame novo da tela.
4. A bolha reaparece e o bitmap é enviado ao OCR local.
5. O painel é classificado como equipamento, comparação da forja, montaria,
   pet, Skills ou totais.
6. O app aplica o recorte normalizado correspondente ao painel e extrai nome,
   raridade, nível, atributos principais e até dois substats.
7. Os valores são comparados com a build persistida e o bitmap é liberado.
8. A sobreposição apresenta `MANTER`, `VENDER` ou `INCONCLUSIVO`, além do delta
   e dos atributos alterados.

Não há captura contínua, armazenamento de screenshots, backend, analytics ou
permissão de armazenamento. O manifesto também remove explicitamente as
permissões de internet e de estado de rede.

## Modelo da build

A build possui 13 fontes:

| Grupo | Fontes |
|---|---|
| Equipamentos | Cabeça, torso, luva, colar, anel, arma, bota e cinto |
| Companheiros | Uma montaria e `Pet 1`, `Pet 2`, `Pet 3` |
| Agregado | Skills, contendo somente Base Damage e Base Health |

Cada fonte armazena, quando aplicável: identificador do slot, nome, raridade,
nível, Base Damage, Base Health, zero a dois substats, confiança do OCR e data
da leitura. O nome não identifica os slots de pet, pois dois pets podem ter o
mesmo nome e nível com atributos diferentes.

## Fórmula de dano

Todos os cálculos usam `BigDecimal` com `MathContext.DECIMAL128`. A decisão é
feita com o valor completo; arredondamento para duas casas ocorre apenas na
exibição do percentual.

Primeiro, o app soma o Base Damage de todas as fontes que o concedem:

```text
baseDamageTotal = Σ Base Damage
```

Os percentuais são somados por tipo antes de virarem multiplicadores. No modo
ativo, o dano esperado é:

```text
criticalChanceEffective = min(criticalChanceTotal / 100, 1)
doubleChanceEffective   = min(doubleChanceTotal / 100, 1)

criticalDamageEffective = 0,20 + criticalDamageTotal / 100
modeDamage = meleeDamageTotal  / 100, quando o modo é MELEE
modeDamage = rangedDamageTotal / 100, quando o modo é RANGED

multiplier =
    (1 + damageTotal / 100)
  × (1 + attackSpeedTotal / 100)
  × (1 + doubleChanceEffective)
  × (1 + criticalChanceEffective × criticalDamageEffective)
  × (1 + modeDamage)

totalDamage = baseDamageTotal × multiplier
```

O modelo considera `20%` como dano crítico-base. Apenas Critical Chance e
Double Chance recebem teto de `100%`. O bônus do modo inativo é ignorado:
Ranged Damage não afeta uma comparação Melee e vice-versa.

Base Health, Lifesteal, Block, Health Regen, Skill Damage e Skill Cooldown são
preservados nos registros, mas não alteram a recomendação de dano deste MVP.
Essa é uma estimativa determinística de dano esperado, não uma simulação do
combate completo do jogo.

## Decisão entre atual e candidato

Para uma fonte candidata, o app constrói duas builds que diferem somente no
slot avaliado:

```text
damageBefore = damage(build atual)
damageAfter  = damage(build atual - fonte atual + candidato)
delta        = damageAfter / damageBefore - 1
```

A classificação usa os valores não arredondados:

| Condição | Resultado | Cor |
|---|---|---|
| `damageAfter > damageBefore` | `MANTER` | Verde |
| `damageAfter < damageBefore` | `VENDER` | Vermelho |
| Valores iguais ou leitura insegura | `INCONCLUSIVO` | Neutra |

Em uma comparação de pet, o candidato é avaliado separadamente contra os três
slots. O melhor cenário é o de maior `delta`, não necessariamente o de maior
dano absoluto, porque cada pet pode ter um nível-alvo diferente.

## Normalização de nível

O nível representa investimento transferível entre pets ou montarias e não
deve, sozinho, fazer um candidato de nível baixo parecer pior. Ao mesmo tempo,
nomes e raridades diferentes podem possuir Base Damage e Base Health próprios.
Por isso, a projeção sempre parte dos valores reconhecidos do próprio pet ou
montaria, sem substituir esses valores por uma base genérica.

O nível comum da comparação é o maior entre as duas fontes:

```text
targetLevel = max(currentLevel, candidateLevel)
```

Nenhuma fonte é reduzida. A de menor nível é projetada para cima, enquanto a de
maior nível conserva os valores lidos.

### Pets: crescimento composto de 1% por nível

```text
normalizedValue = readValue × 1,01 ^ (targetLevel - readLevel)
```

Exemplo medido entre os níveis 1 e 100, que possuem 99 incrementos:

```text
Base Damage: 28,5 × 1,01^99 = 76,323955  ≈ 76,4 exibido no jogo
Base Health: 67,2 × 1,01^99 = 179,963851 ≈ 179,9 exibido no jogo
```

Cada cenário de pet calcula seu próprio alvo:

```text
targetPet1 = max(candidateLevel, pet1Level)
targetPet2 = max(candidateLevel, pet2Level)
targetPet3 = max(candidateLevel, pet3Level)
```

Assim, o mesmo candidato pode receber três projeções diferentes sem perder o
nome, a raridade, os valores-base próprios ou os substats.

### Montarias: crescimento composto de 0,6% por nível

```text
normalizedValue = readValue × 1,006 ^ (targetLevel - readLevel)
```

Exemplo medido entre os níveis 1 e 57, que possuem 56 incrementos:

```text
Base Damage: 220  × 1,006^56 = 307,545659  ≈ 307 exibido no jogo
Base Health: 1760 × 1,006^56 = 2460,365271 ≈ 2460 exibido no jogo
```

As pequenas diferenças são compatíveis com a precisão interna e o
arredondamento ou truncamento da interface do jogo.

A sobreposição identifica esses resultados como `NORMALIZADO LV. X`. Se o
nível atual ou candidato não for reconhecido, a comparação permanece
`INCONCLUSIVO`; o app não inventa um nível. A normalização remove o viés dos
níveis observados, mas não tenta calcular experiência adicional recebida numa
fusão.

## OCR e controle de confiança

O OCR usa `com.google.mlkit:text-recognition:16.0.1`, empacotado no APK para
funcionar offline. O pipeline combina:

- classificação do painel pelo texto global visível;
- recortes normalizados específicos para cada um dos seis tipos de tela;
- leitura ampla dos atributos;
- leituras focadas e ampliadas da região do nome;
- variações com máscara de cor para melhorar texto estilizado;
- concordância entre múltiplas leituras antes de aceitar um nome;
- separação posicional entre item equipado e candidato na tela da forja.

A confiança mínima inicial é `0,85`. A confiança efetiva de uma fonte usa o
menor valor relevante entre os campos reconhecidos. Campo obrigatório ausente,
percentual inválido, nome incompatível ou confiança abaixo do limite mantém a
leitura inconclusiva e editável.

O detector de slot começa com termos conhecidos, como `Ring`, `Sword`, `Grip`,
`Impulse`, `Greaves` e `Belt`. Quando o jogo compara um nome ainda desconhecido
com um item de slot já comprovado, essa associação exata pode ser aprendida no
catálogo local. Não é usada correção aproximada para tentar adivinhar erros de
OCR.

## Persistência e privacidade

Um único Preferences DataStore mantém pequenos documentos JSON para:

- build ativa;
- calibração em andamento;
- modo Melee ou Ranged;
- última troca para desfazer;
- ajustes de recorte;
- catálogo local de nomes de equipamentos.

Os candidatos não formam histórico. Uma troca confirmada guarda apenas a fonte
anterior para um único `Desfazer`. O APK define `allowBackup=false`, não solicita
armazenamento e não contém as imagens de referência usadas nos testes.

## Arquitetura do código

O aplicativo mantém um único módulo Android e separa apenas responsabilidades
que precisam ser testadas:

| Pacote | Responsabilidade |
|---|---|
| `model` | Build, fontes, campos reconhecidos e resultados |
| `parser` | Números abreviados, percentuais, níveis, substats e slots |
| `scoring` | Fórmula de dano, substituições e normalização de nível |
| `ocr` | Classificação de painel, recortes e ML Kit |
| `storage` | Preferences DataStore e serialização JSON |
| `overlay` | MediaProjection, bolha e cartões de resultado |
| `ui` | Activity Compose, calibração e ajustes de recorte |

Não há backend, banco relacional, Hilt, Firebase, telemetria, anúncios ou NDK.

## Tecnologias e versões

- Kotlin `2.3.21` e Java/JBR 17;
- Android Gradle Plugin `9.3.0`;
- Gradle Wrapper `9.5.0`;
- Jetpack Compose BOM `2026.06.00`;
- Android `compileSdk = targetSdk = minSdk = 36`;
- ML Kit Text Recognition bundled `16.0.1`;
- Preferences DataStore `1.2.1`;
- Kotlin coroutines `1.10.2` e serialization JSON `1.9.0`.

## Compilar

Requisitos:

- Android Studio Quail 3 `2026.1.3 Patch 1` ou ambiente compatível;
- SDK Android 16/API 36;
- JBR incluído no Android Studio.

No diretório `10_app_android/`:

```bash
./gradlew assembleDebug
```

O APK será criado em:

```text
10_app_android/app/build/outputs/apk/debug/app-debug.apk
```

O repositório não versiona APKs, chaves de assinatura, `local.properties`,
caches ou builds locais.

## Testes

Validação principal:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Com um emulador ou aparelho Android 16 conectado:

```bash
./gradlew connectedDebugAndroidTest
```

O estado atual possui 17 testes unitários para parser, fórmula, tetos,
Melee/Ranged, substituições, pets, normalização e casos inconclusivos. Há três
testes instrumentados para OCR das 15 capturas de referência, persistência,
desfazer e controles essenciais da interface. O Android Lint conclui sem erros.

As capturas são adicionadas somente ao source set `androidTest`; não entram no
APK principal.

## Estrutura do repositório

```text
10_app_android/   projeto Android
20_referencias/   solver e capturas usados na validação
docs/             escopo canônico e decisões do MVP
90_saidas/        checklist de aceite; APK local ignorado pelo Git
```

Consulte também:

- [`docs/escopo_mvp.md`](docs/escopo_mvp.md): requisitos e fórmulas canônicas;
- [`10_app_android/README.md`](10_app_android/README.md): detalhes operacionais
  do projeto Android;
- [`90_saidas/checklist_aceite_s24.md`](90_saidas/checklist_aceite_s24.md):
  aceite físico no Galaxy S24 Ultra.

## Limitações conhecidas

- suporte intencional apenas ao Android 16/API 36 neste MVP;
- idioma, orientação e layout do jogo devem corresponder às referências;
- mudanças na interface exigem revisão ou recalibração dos recortes;
- OCR continua sujeito a fonte estilizada, animações e sobreposição de efeitos;
- a fórmula estima dano esperado e não modela defesa inimiga, animações,
  cooldowns, duração da luta ou mecânicas ocultas;
- normalização de nível usa curvas empíricas observadas e não calcula a
  experiência resultante de uma fusão;
- ainda falta concluir todo o checklist físico no Galaxy S24 Ultra.

## Licença

O código-fonte é distribuído sob a licença MIT; consulte [`LICENSE`](LICENSE).
Marcas, nomes e elementos visuais do jogo presentes nas referências pertencem
aos respectivos titulares.
