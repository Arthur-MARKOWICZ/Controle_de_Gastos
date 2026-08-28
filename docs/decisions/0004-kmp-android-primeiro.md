# ADR-004: Kotlin Multiplatform com validação Android primeiro

## Status

Aceito

## Data

2026-08-27

## Contexto

Android é o foco imediato e iPhone é desejável. Compose Multiplatform está estável em ambas as plataformas, mas o ambiente atual não possui macOS/Xcode.

## Decisão

Compartilhar domínio, acesso à API e interface principal com Kotlin Multiplatform/Compose. Manter os alvos e pontos de entrada de iOS no repositório, mas validar e distribuir Android primeiro.

## Alternativas consideradas

### Apenas Android nativo

- Vantagem: menor configuração inicial.
- Desvantagem: futura implementação iOS duplicaria lógica e interface.

### UI nativa SwiftUI no iOS

- Vantagem: experiência Apple mais específica.
- Desvantagem: exige uma segunda UI e conhecimento Swift sem necessidade comprovada.

## Consequências

- Nenhuma entrega poderá afirmar suporte iOS sem compilação e testes em Mac.
- Integrações de notificações e armazenamento seguro terão adaptadores por plataforma.
- A interface compartilhada deve respeitar comportamentos e acessibilidade específicos de cada plataforma.
- A máquina atual contém somente o Android SDK 37, mas o AGP 9.1 declara testes até 36.1. O build Android passa com aviso; a CI deverá instalar uma combinação oficialmente suportada em vez de suprimir o alerta.
