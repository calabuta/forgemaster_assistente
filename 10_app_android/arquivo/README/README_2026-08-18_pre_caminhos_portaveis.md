# Aplicativo Android

Projeto Android enxuto do ForgeMaster Assistente: um módulo `app`, uma Activity
Compose, um serviço MediaProjection e um Preferences DataStore.

## Requisitos locais

- Android Studio Quail 3 `2026.1.3 Patch 1`.
- JBR incluído no Android Studio.
- Android SDK 36 em `/Users/joao/Library/Android/sdk`.
- AVD ARM64 `ForgeMaster_API_36` ou Galaxy S24 Ultra com Android 16.

## Configuração do projeto

- Kotlin e Jetpack Compose.
- `compileSdk = targetSdk = minSdk = 36`.
- AGP `9.3.0`, Gradle `9.5.0` e Compose BOM `2026.06.00`.
- ML Kit Text Recognition bundled `16.0.1`.
- DataStore Preferences com seis registros JSON, incluindo o catálogo local de
  nomes de equipamentos aprendidos nas comparações.
- Sem backend, banco, Hilt, telemetria, anúncios, internet ou armazenamento.

## Compilar

Na pasta `10_app_android/`:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_SDK_ROOT="/Users/joao/Library/Android/sdk" \
./gradlew assembleDebug
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`.

## Testar

Com o AVD ou aparelho Android 16 conectado:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_SDK_ROOT="/Users/joao/Library/Android/sdk" \
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug
```

As capturas em `../20_referencias/telas/` são empacotadas somente no APK de
teste instrumentado. O APK do aplicativo foi verificado sem essas imagens.

## Estrutura do código

- `model`: fontes, build, calibração, resultado e recortes.
- `parser`: números, percentuais, níveis e substats.
- `scoring`: fórmula canônica e substituições.
- `ocr`: classificação dos seis painéis, recorte e ML Kit.
- `storage`: único DataStore, inclusive para o catálogo local de nomes.
- `overlay`: captura sob demanda, bolha e cartão de resultado.
- `ui`: tela principal, edição manual e recortes com prévia.

## Permissões do APK

- notificações;
- sobreposição;
- serviço em primeiro plano;
- MediaProjection em serviço em primeiro plano.

O APK não solicita internet, estado de rede ou armazenamento.

## Resultado da validação local

- 9 testes unitários aprovados.
- 3 testes instrumentados aprovados no AVD Android 16/API 36.
- OCR dos valores das 15 capturas aprovado.
- Android Lint: 0 erros; permanecem apenas avisos compatíveis com as versões
  fixadas no plano e com o alvo exclusivo Android 16.

O aceite físico no Galaxy S24 Ultra permanece pendente.
