package com.lojanocarro

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private class Passo(val emoji: String, val titulo: String, val texto: String, val fala: String)

private val PASSOS = listOf(
    Passo(
        "👆", "Toque no que você quer",
        "Balinha, chocolate, refri ou água. Toque no produto para adicionar.",
        "Toque nas balinhas, no chocolate, no refrigerante ou na água que você quiser."
    ),
    Passo(
        "📲", "Pague com Pix",
        "Toque em Pagar com Pix e leia o QR Code com o app do seu banco.",
        "Toque em Pagar com Pix e leia o código com o aplicativo do seu banco."
    ),
    Passo(
        "🎁", "Pronto, é só receber",
        "Assim que o Pix cair, o motorista entrega o seu pedido.",
        "Assim que o pagamento for confirmado, o motorista entrega o seu pedido."
    )
)

private const val PASSO_MS = 6_500L

/**
 * Tela de descanso (modo atração): aparece quando ninguém está comprando e explica como pedir.
 * Se existir o arquivo app/src/main/res/raw/tutorial.mp4, ele toca em loop; senão roda um tutorial animado.
 * O primeiro toque só fecha esta tela, não adiciona nada ao carrinho.
 */
@Composable
fun TelaDescanso(
    config: LojaConfig,
    onFalar: (String) -> Unit,
    onPararFala: () -> Unit,
    onToque: () -> Unit
) {
    val ctx = LocalContext.current
    // Procura o vídeo em tempo de execução: sem o arquivo, o app compila e roda igual.
    val videoId = remember { ctx.resources.getIdentifier("tutorial", "raw", ctx.packageName) }

    DisposableEffect(Unit) { onDispose { onPararFala() } }

    Box(Modifier.fillMaxSize().background(Paleta.Noite)) {
        if (videoId != 0) {
            VideoTutorial(videoId, comSom = config.narrarTutorial)
        } else {
            TutorialAnimado(config.produtos, narrar = config.narrarTutorial, onFalar = onFalar)
        }

        ChamadaParaTocar(Modifier.align(Alignment.BottomCenter))

        // Camada de cima: captura o toque que acorda a loja.
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToque
            )
        )
    }
}

@Composable
private fun VideoTutorial(resId: Int, comSom: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            VideoView(c).apply {
                setVideoURI(Uri.parse("android.resource://${c.packageName}/$resId"))
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    if (!comSom) mp.setVolume(0f, 0f)
                    start()
                }
            }
        }
    )
}

@Composable
private fun TutorialAnimado(produtos: List<Produto>, narrar: Boolean, onFalar: (String) -> Unit) {
    var passo by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(PASSO_MS)
            passo = (passo + 1) % PASSOS.size
        }
    }
    LaunchedEffect(passo, narrar) { if (narrar) onFalar(PASSOS[passo].fala) }

    val pulso by rememberInfiniteTransition(label = "pulso").animateFloat(
        initialValue = 1f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "escala"
    )

    Column(
        Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 36.dp, bottom = 130.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Peça aqui, pague com Pix",
            style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))

        Crossfade(targetState = passo, label = "passo") { i ->
            val p = PASSOS[i]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(150.dp).scale(pulso).clip(CircleShape).background(Paleta.Amarelo),
                    contentAlignment = Alignment.Center
                ) { Text(p.emoji, fontSize = 76.sp) }
                Spacer(Modifier.height(28.dp))
                Text(p.titulo, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    p.texto, fontSize = 18.sp, lineHeight = 26.sp, color = Paleta.TextoSuave,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp)
                )
                if (i == 0) {
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        produtos.take(5).forEach {
                            Box(
                                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(it.corFundo()),
                                contentAlignment = Alignment.Center
                            ) { Text(it.emoji, fontSize = 28.sp) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PASSOS.indices.forEach { i ->
                Box(
                    Modifier.height(10.dp).width(if (i == passo) 28.dp else 10.dp)
                        .clip(CircleShape).background(if (i == passo) Paleta.Amarelo else Paleta.Linha)
                )
            }
        }
    }
}

@Composable
private fun ChamadaParaTocar(modifier: Modifier) {
    val escala by rememberInfiniteTransition(label = "toque").animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "escala"
    )
    Box(
        modifier.padding(24.dp).scale(escala).clip(CircleShape).background(Paleta.Amarelo)
            .padding(horizontal = 36.dp, vertical = 20.dp)
    ) {
        Text("Toque para começar", color = Paleta.Noite, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
    }
}
