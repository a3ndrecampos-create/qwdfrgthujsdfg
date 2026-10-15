# Loja no Carro (Kotlin + Jetpack Compose)

Totem de autoatendimento para o passageiro (bala, refri, chocolate, batata, água) + notificação de pedido no celular do motorista.

## Como funciona
- **Modo Loja** (tablet do passageiro): catálogo com fotos → carrinho → QR Code Pix → tela "pago".
- **Modo Motorista** (seu celular): recebe o pedido por notificação, vê os itens, confirma o Pix manualmente e configura a loja
  (botão "⚙ Configurar loja": chave Pix, nome, cidade, produtos e preços).
- Mesmo APK nos dois aparelhos; o modo é escolhido na primeira tela.
- Os aparelhos se falam por **Nearby Connections** (Bluetooth + Wi-Fi Direct, sem internet). No 1º pareamento os dois mostram o mesmo
  código de 4 dígitos; depois se reconhecem sozinhos e reconectam automaticamente se a conexão cair.

## Pix
- QR estático "copia e cola" gerado no próprio app com a chave do motorista (`PixBrCode.kt`).
- O Pix estático não avisa o app sozinho: o motorista toca em "Confirmei o Pix" depois de ver o dinheiro no app do banco.
- Confirmação automática (cobrança dinâmica via PSP + webhook) fica para uma etapa futura.

## Antes do primeiro uso
1. Abra o modo Motorista → "⚙ Configurar loja" e troque a chave Pix, nome e cidade de exemplo pelos seus.
2. Fotos dos produtos: coloque arquivos quadrados (~600x600) em `app/src/main/assets/produtos/` com os nomes
   `bala.jpg`, `refri.jpg`, `choco.jpg`, `batata.jpg`, `agua.jpg`. Sem a foto, o card mostra um emoji.

## Gerar o APK pelo GitHub (sem Android Studio)
```
git init
git add .
git commit -m "Primeira versão"
git branch -M main
git remote add origin https://github.com/SEU_USUARIO/loja-no-carro.git
git push -u origin main
```
O workflow `.github/workflows/build-apk.yml` roda sozinho a cada push na `main` (ou manualmente:
`gh workflow run build-apk.yml`). Quando terminar, baixe o APK em **Actions → último run → Artifacts → loja-no-carro-debug**
(ou `gh run download`). Instale o mesmo APK no tablet e no celular.

O projeto não inclui `gradlew`: o workflow instala o Gradle 8.9 no próprio runner. Se quiser o wrapper no futuro, rode `gradle wrapper`
numa máquina com Gradle instalado e faça commit dos arquivos gerados.

## Stack
Kotlin 2.0.21, AGP 8.7.3, Compose (BOM 2024.10.01), Material 3, Nearby Connections, kotlinx.serialization, DataStore, Coil, ZXing.
minSdk 26, targetSdk 35, pacote `com.lojanocarro`.

## Próximos passos
1. Modo quiosque no tablet (screen pinning) e foreground service no celular do motorista, para o app não ser encerrado em segundo plano.
2. Botão "Esquecer tablets" na configuração (a função `esquecerTablets()` já existe no `NearbyLink`).
3. Backend + PSP para confirmação automática do Pix.
4. Assinatura de release (keystore) para publicar na Play Store.
5. Venda de produtos dentro de carro de app tem regras da plataforma e do município; confira antes de operar.
