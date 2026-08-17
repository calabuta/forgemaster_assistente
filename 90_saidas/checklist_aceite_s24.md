# Checklist de aceite — Galaxy S24 Ultra

Preencher no aparelho-alvo antes de considerar o MVP aceito.

## Identificação

- Data:
- Versão do Android:
- Versão da One UI:
- Número da compilação:
- APK: `ForgeMaster-Assistente-mvp-debug.apk`

## Instalação e permissões

- [ ] O S24 aparece como `device` em `adb devices`.
- [ ] O APK instala com `adb install -r ForgeMaster-Assistente-mvp-debug.apk`.
- [ ] O app abre sem erro.
- [ ] A permissão de notificações é solicitada.
- [ ] A permissão de sobreposição é solicitada e aceita pela One UI.
- [ ] O consentimento de captura de tela aparece ao iniciar cada sessão.
- [ ] O app não solicita internet nem acesso a arquivos/fotos.

## Calibração

- [ ] É possível revisar e confirmar as 8 peças, a montaria, os 3 pets e Skills.
- [ ] A build não é salva antes da confirmação final do rascunho.
- [ ] Uma calibração interrompida continua do ponto pendente.
- [ ] A recalibração parcial altera somente a fonte escolhida.
- [ ] O toque longo permite escolher a fonte para calibrar/recalibrar.
- [ ] O ajuste dos seis recortes abre com a captura como prévia.
- [ ] Posição e tamanho do recorte podem ser ajustados.
- [ ] `Restaurar padrões` repõe os recortes originais.

## Bolha e captura

- [ ] A bolha aparece sobre o Forge Master e pode ser movida.
- [ ] Um toque esconde a bolha, captura um frame novo e a restaura.
- [ ] Não há análise contínua nem aquecimento anormal em repouso.
- [ ] O cartão mostra modo ativo, valores lidos, delta e recomendação.
- [ ] Alterar um valor reconhecido recalcula o resultado imediatamente.
- [ ] O modo Melee/Ranged pode ser alternado no cartão e permanece após reabrir.
- [ ] O cartão oferece `Equipei no jogo`, descartar e desfazer.
- [ ] O app nunca toca em `Sell`, `Equip` ou `Remove` no jogo.

## Comparações

- [ ] Na forja, equipado e candidato são separados corretamente.
- [ ] Se o slot do equipamento for incerto, o app pede seleção manual.
- [ ] Uma montaria candidata é comparada somente com a montaria salva.
- [ ] Um pet candidato gera três cenários e destaca a melhor substituição.
- [ ] A confirmação do pet registra qual Pet 1/2/3 foi removido.
- [ ] Após confirmar uma troca, o candidato seguinte usa a build atualizada.
- [ ] `Desfazer última troca` restaura somente a fonte anterior.
- [ ] Delta exatamente zero aparece como `INCONCLUSIVO — dano equivalente`.
- [ ] Leitura inválida ou abaixo de 0,85 permanece `INCONCLUSIVO` até revisão.

## Funcionamento offline

- [ ] Fechar o app e ativar modo avião.
- [ ] Iniciar uma nova sessão de captura.
- [ ] OCR, comparação, correção e persistência continuam funcionando.

## Resultado

- [ ] Aceito no Galaxy S24 Ultra.
- [ ] Reprovado; registrar abaixo o passo, tela e comportamento observado.

Observações:
