# AGENTS.md — ForgeMaster Assistente

## Leitura mínima

Antes de trabalhar neste projeto, ler:

1. `README.md`, para navegação e estado resumido;
2. `MEMORY.md`, para contexto operacional, pendências e continuidade;
3. `docs/escopo_mvp.md`, como fonte canônica dos requisitos, regras do jogo,
   fórmulas e critérios de aceite.

Se esses arquivos divergirem, não escolher silenciosamente uma versão. Apontar
a divergência e preservar `docs/escopo_mvp.md` como fonte canônica do produto
até que o usuário decida de outra forma.

## Governança da informação

- Manter neste `AGENTS.md` somente instruções sobre como o Codex deve trabalhar
  no projeto: leitura, segurança, roteamento, implementação e validação.
- Registrar em `docs/escopo_mvp.md` requisitos funcionais, regras de negócio,
  fórmulas, modelo de dados, fluxos, restrições técnicas e critérios de aceite.
- Registrar em `MEMORY.md` apenas estado atual, pendências, dúvidas, contexto de
  continuidade e fatos operacionais que possam ser úteis em sessões futuras.
- Usar `README.md` para visão geral, navegação e indicação das fontes
  canônicas, sem reproduzir a especificação completa.
- Cada informação deve ter uma única fonte canônica. Nos demais arquivos,
  apontar para ela em vez de copiar o conteúdo.
- Antes de atender a pedidos como “lembre isso”, “salve no projeto” ou
  “registre essa decisão”, classificar a informação pelo seu papel, e não por
  ser estável ou mutável:
  - comportamento obrigatório do Codex: `AGENTS.md`;
  - definição do produto ou decisão técnica: `docs/escopo_mvp.md`;
  - estado, pendência ou contexto de continuidade: `MEMORY.md`;
  - navegação ou apresentação do projeto: `README.md`.
- Quando uma informação misturar mais de uma categoria, separar as partes e
  registrar cada uma no destino correto.
- Não usar `MEMORY.md` como substituto para especificação nem `AGENTS.md` como
  catálogo de fatos do produto.

## Regras de trabalho

- Não iniciar implementação, criar projeto Gradle, código Android ou APK sem
  novo pedido explícito do usuário.
- Não alterar requisitos do produto por inferência. Registrar dúvidas e pedir
  confirmação quando uma escolha mudar o comportamento esperado do app.
- Tratar `20_referencias/ForgeMaster.xlsx` e as capturas em
  `20_referencias/telas/` como fontes originais; nunca sobrescrevê-las.
- Não enviar as referências, capturas ou dados do usuário para serviços
  externos sem autorização explícita.
- Manter alterações pequenas, revisáveis e restritas ao pedido atual.
- Ao alterar uma definição do produto, atualizar primeiro
  `docs/escopo_mvp.md`; ajustar `README.md` ou `MEMORY.md` somente se o resumo,
  a navegação ou o estado operacional também tiverem mudado.

## Implementação e validação futura

Quando a implementação for autorizada:

- manter captura, OCR, normalização, cálculo, persistência e interface em
  componentes testáveis;
- validar primeiro as fórmulas e o parser numérico com testes unitários;
- validar o OCR com as capturas preservadas antes dos testes ao vivo;
- testar as permissões, a captura e a sobreposição no aparelho-alvo definido
  em `docs/escopo_mvp.md`;
- verificar os critérios de aceite da especificação antes de declarar uma
  etapa concluída;
- registrar em `MEMORY.md` somente o resultado operacional da sessão, as
  pendências e as decisões ainda não incorporadas à documentação canônica.
