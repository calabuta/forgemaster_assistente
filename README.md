# ForgeMaster Assistente

Aplicativo Android pessoal, inspirado no fluxo do Poke Genie, que lê telas do
Forge Master localmente e compara o dano da build atual com um candidato.

Repositório privado: <https://github.com/calabuta/forgemaster_assistente>.

## Estado

- MVP Android implementado em `10_app_android/`.
- APK debug local gerado em `90_saidas/ForgeMaster-Assistente-mvp-debug.apk` e
  intencionalmente ignorado pelo Git; após clonar, gere-o conforme
  `10_app_android/README.md`.
- Ambiente preparado no Mac com Android Studio, SDK Android 16/API 36 e um AVD
  ARM64 `ForgeMaster_API_36`.
- Parser, fórmula, substituições, pets, persistência e desfazer validados por
  testes automatizados.
- OCR bundled validado no emulador sobre as 15 capturas originais; as imagens
  entram somente no APK de teste, nunca no APK do aplicativo.
- Validação física no Galaxy S24 Ultra ainda pendente porque o aparelho não
  estava conectado durante esta entrega.

## Como usar o APK no S24 Ultra

1. Ative as opções do desenvolvedor e a depuração USB no aparelho.
2. Conecte-o ao Mac e confirme que aparece em `adb devices`.
3. Instale o APK:

   ```bash
   adb install -r "90_saidas/ForgeMaster-Assistente-mvp-debug.apk"
   ```

4. Abra `ForgeMaster Assistente`, escolha `Melee` ou `Ranged` e inicie a bolha.
5. Conceda notificações, sobreposição e captura de tela quando o Android pedir.
6. Faça a calibração das 13 fontes e confirme o rascunho somente após revisar
   os valores.
7. No jogo, toque na bolha para capturar uma tela. Toque longo abre calibração,
   recalibração, recortes com prévia, troca de modo e desfazer.

O app não toca em `Sell`, `Equip` ou `Remove`; essas ações continuam manuais
no jogo.

## Onde está cada coisa

- `docs/escopo_mvp.md`: especificação canônica.
- `10_app_android/README.md`: comandos de build e testes.
- `20_referencias/ForgeMaster.xlsx`: solver original preservado.
- `20_referencias/telas/`: índice e 15 capturas originais preservadas.
- `90_saidas/`: checklist versionado e APK debug somente no workspace local.

## Validações concluídas

- Android 16/API 36 em AVD ARM64.
- 9 testes unitários.
- 3 testes instrumentados: OCR das 15 capturas, persistência/desfazer e
  controles essenciais da interface.
- Android Lint sem erros.
- APK com `minSdk=targetSdk=36`, sem permissão de internet ou armazenamento.
- Capturas de referência ausentes do APK final.

## Pendência real

Executar o checklist de `90_saidas/checklist_aceite_s24.md` no Galaxy S24 Ultra
para validar permissões One UI, captura, sobreposição, recortes e uso offline
no aparelho-alvo.
