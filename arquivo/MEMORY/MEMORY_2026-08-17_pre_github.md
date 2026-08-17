# Memória — ForgeMaster Assistente

_Última atualização: 2026-08-17_

## Estado atual

- MVP Android implementado em `10_app_android/` e compilado com sucesso.
- APK debug de entrega: `90_saidas/ForgeMaster-Assistente-mvp-debug.apk`.
- Especificação canônica: `docs/escopo_mvp.md`.
- Referências originais preservadas em `20_referencias/`.

## Ambiente preparado

- Android Studio Quail 3 `2026.1.3 Patch 1` em `/Applications/Android Studio.app`.
- JBR do Android Studio usado pelo Gradle; nenhum Java separado instalado.
- SDK Android 16/API 36, Build Tools, Platform Tools, Emulator e imagem ARM64
  instalados em `/Users/joao/Library/Android/sdk`.
- AVD Android 16 ARM64 criado como `ForgeMaster_API_36`.

## Validação concluída

- 9 testes unitários cobrem parser, fórmula, tetos, Melee/Ranged, delta zero,
  substituições e pets.
- 3 testes instrumentados no AVD cobrem OCR das 15 capturas, persistência,
  troca atômica, desfazer e controles essenciais da interface.
- Android Lint concluído sem erros.
- APK final não contém as capturas e não solicita internet nem armazenamento.
- A captura da arma de referência lê `Attack Speed` de forma inválida; o app
  mantém o resultado inconclusivo até confirmação/correção manual, conforme o
  requisito de não adivinhar valores.

## Pendência aberta

- Validar no Galaxy S24 Ultra: conexão ADB, permissões One UI, MediaProjection,
  sobreposição, recortes, sequência de candidatos e funcionamento offline.
  O aparelho não estava conectado na conclusão desta implementação.

## Regra de manutenção

- Requisitos e fórmulas permanecem somente em `docs/escopo_mvp.md`.
- Registrar aqui apenas estado, validações, pendências e contexto de retomada.
