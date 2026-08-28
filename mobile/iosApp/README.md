# Entrada iOS

O módulo `shared` exporta o framework `Shared`, e `iosApp.swift` contém o ponto de entrada SwiftUI. O projeto Xcode, signing e build não são gerados nem validados neste ambiente Linux.

Quando houver acesso a um Mac:

1. Gere/abra um projeto iOS no assistente oficial Kotlin Multiplatform.
2. Vincule o framework `Shared` produzido pelo Gradle.
3. Configure Team ID, bundle identifier e capabilities de push.
4. Compile em simulador e dispositivo real.
5. Somente então marque o alvo iOS como suportado.
