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

## Motorista em segundo plano (popup do pedido)
- No celular do motorista o app roda como serviço em primeiro plano (aparece uma notificação fixa "Recebendo pedidos em segundo plano").
  Assim a conexão com o tablet continua viva com o Uber na tela.
- Quando chega um pedido, aparece um **popup** (notificação de alta prioridade) por cima de qualquer app, com os itens, o total e o
  botão **Confirmar Pix recebido**. Dá para confirmar sem abrir o app. O popup é breve, mas o pedido fica na área de notificações.
- Na primeira vez, aceite as notificações e toque em **Permitir** no aviso âmbar da tela do motorista (deixa o app fora da economia
  de bateria, senão alguns celulares encerram o serviço). Em Xiaomi, Samsung e outros, vale também liberar "iniciar automaticamente"
  e "sem restrições de bateria" nas configurações do app.
- O botão **Encerrar** da notificação fixa desliga o serviço.

## Loja aberta e trava do tablet
- No celular do motorista, o interruptor **Loja aberta / Loja fechada** controla o tablet.
- **Aberta:** o tablet entra em tela cheia, sem barras, com o botão voltar bloqueado e o app fixado (sem Home/Recentes).
  **Fechada:** o tablet fica liberado e mostra "Loja fechada". A loja começa fechada.
- Só o app do motorista destrava, pelo interruptor. Se o celular descarregar, existe o **PIN de emergência** (definido em "Configurar loja"):
  toque 7 vezes no canto superior esquerdo do tablet e digite o PIN. Depois de 5 erros ele espera 60 segundos.
  Sem PIN, só o celular destrava.
- O tablet lembra a função e o estado: se o app for fechado ou reiniciado com a loja aberta, ele volta travado sozinho.
- **Ligue o recurso do Android no tablet** (uma vez): Configurações > Segurança (ou Biometria e segurança) > Outras configurações de
  segurança > **Fixar apps** (às vezes "Fixar janela"). Ative também **Pedir PIN antes de liberar** e deixe uma tela de bloqueio no tablet.
  Sem isso o Android não deixa o app se fixar. Na primeira vez ele mostra um aviso "App fixado"; toque em Entendi.
- **Limite honesto:** é a fixação de app do próprio Android. Quem souber o gesto do sistema (segurar Voltar + Recentes) consegue sair,
  mas com "Pedir PIN antes de liberar" precisa do PIN do tablet. Para travar de verdade (sem nenhuma saída) é preciso o modo quiosque de
  administrador do aparelho, que exige configurar o tablet pelo computador. Posso fazer essa versão se você quiser.

## Tela de descanso (tutorial automático)
- A loja começa nela e volta a ela depois de 60 segundos sem toque (o carrinho é esvaziado). O primeiro toque só fecha a tela.
- Sem vídeo, roda um tutorial animado em 3 passos (escolher, pagar com Pix, receber).
- Para usar um vídeo seu: crie a pasta `app/src/main/res/raw/` e coloque `tutorial.mp4` (nome em minúsculas, vertical, MP4/H.264,
  uns 20 a 40 segundos). Ele toca em loop e substitui a animação. Sem o arquivo, o app compila e roda igual.
- A tela do tablet fica sempre ligada enquanto está no modo loja.

## Voz (opcional, em "Configurar loja")
- **Falar os itens ao confirmar o Pix**: o celular do motorista diz, por exemplo, "Pix confirmado. Entregar: 2 unidades de Bala de goma.
  1 unidade de Água mineral 500ml. Total: 5 reais." Ligado por padrão.
- **Narrar o tutorial no tablet**: o tablet fala cada passo do tutorial (ou liga o som do vídeo). Desligado por padrão.
- Usa a voz do próprio Android, sem internet. Precisa de uma voz em português do Brasil instalada
  (Configurações > Sistema > Idioma > Texto para fala).

## Antes do primeiro uso
1. Abra o modo Motorista → "⚙ Configurar loja" e troque a chave Pix, nome e cidade de exemplo pelos seus.
2. Fotos dos produtos: em "Configurar loja", toque na foto de cada produto e escolha uma da galeria (sem pedir permissão).
   O app recorta em quadrado, reduz para 480 px e salva no celular. Quando o tablet estiver conectado, ele pede as fotos que faltam
   e o celular manda sozinho (a primeira vez leva alguns segundos). PNG com fundo transparente mantém a transparência.
   Sem foto, o card mostra um emoji sobre a cor do produto. (Dá também para embutir fotos no app, na pasta
   `app/src/main/assets/produtos/`, com o nome indicado em `CatalogoFicticio.kt`.)

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
