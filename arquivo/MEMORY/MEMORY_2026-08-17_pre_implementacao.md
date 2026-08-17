# Memória — ForgeMaster Assistente

_Última atualização: 2026-08-17_

## Estado atual

- Projeto documental criado e viabilidade técnica inicial confirmada.
- Implementação Android ainda não iniciada.
- O usuário determinou que o app permaneça pausado até novo pedido explícito.
- Não existem projeto Gradle, código Android nem APK neste momento.

## Contexto para continuidade

- A especificação canônica do produto está em `docs/escopo_mvp.md`.
- O solver e as capturas fornecidas pelo usuário estão preservados em
  `20_referencias/` e devem continuar sendo tratados como fontes originais.
- A análise do Excel já separou a fórmula aproveitável do dano das escolhas do
  Solver teórico. As conclusões aplicáveis ao app estão registradas na
  especificação, sem necessidade de reaplicá-las a partir desta memória.
- As definições da build, das fontes, do aparelho-alvo, dos modos de arma, da
  calibração e da comparação de itens estão consolidadas na especificação.
- As quinze capturas disponíveis estão indexadas em
  `20_referencias/telas/README.md`.
- O conjunto visual atual inclui a lista rolável de atributos totais e o
  detalhe individual dos três pets ativos.

## Pendências abertas

- Preparar o ambiente Android no Mac: a verificação de 2026-08-17 não encontrou
  Java, Android Studio, Android SDK nem `adb` instalados.
- Validar OCR, dimensões de recorte, permissões de captura e comportamento da
  sobreposição no aparelho-alvo quando a implementação for autorizada.

## Regra de manutenção desta memória

- Registrar aqui somente mudanças de estado, pendências, dúvidas e contexto de
  continuidade.
- Não copiar para cá requisitos ou fórmulas de `docs/escopo_mvp.md`.
- Quando uma pendência for resolvida e virar definição do produto, mover a
  conclusão para a especificação e remover a pendência desta memória.
