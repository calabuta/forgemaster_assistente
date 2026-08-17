# Aplicativo Android

Diretório apenas reservado para uma possível implementação futura do
ForgeMaster Assistente. Não há código, projeto Gradle nem APK criado. A
implementação só deve começar após novo pedido explícito do usuário.

## Stack definida para o protótipo

- Kotlin.
- Jetpack Compose.
- MediaProjection para captura de tela autorizada pelo usuário.
- ML Kit Text Recognition com modelo on-device.
- Janela de sobreposição para exibir a recomendação sobre o jogo.
- DataStore para manter a build calibrada e as preferências localmente.

## Módulos lógicos previstos

- `capture`: sessão de captura e recorte das regiões relevantes.
- `ocr`: reconhecimento do texto e confiança da leitura.
- `parser`: normalização de nomes, porcentagens e sufixos `k/m/b`.
- `domain`: atributos, build, item e substituição de slot.
- `scoring`: fórmula de dano e delta percentual.
- `overlay`: resultado `EQUIPAR`, `VENDER` ou `INCONCLUSIVO`.

O projeto Gradle será criado na etapa de implementação do MVP.
