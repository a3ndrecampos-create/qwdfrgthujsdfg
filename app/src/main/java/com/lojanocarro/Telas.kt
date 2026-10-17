package com.lojanocarro

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun reais(c: Int) = "R$ %d,%02d".format(c / 100, c % 100)

fun qr(texto: String, tam: Int = 600): Bitmap {
    val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tam, tam)
    val px = IntArray(tam * tam) { i -> if (m[i % tam, i / tam]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, tam, tam, Bitmap.Config.ARGB_8888)
}

/** Estado da conexão entre os dois aparelhos: bolinha verde quando conectado, âmbar nos outros casos. */
@Composable
fun StatusPill(estado: String) {
    val ok = estado == "Conectado"
    val cor = if (ok) Paleta.Verde else Paleta.Ambar
    Row(
        Modifier.clip(CircleShape).background(cor.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(cor))
        Spacer(Modifier.width(8.dp))
        Text(
            if (estado.isBlank()) "Sem conexão" else estado,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
        )
    }
}

// ---------- Primeira tela ----------
@Composable
fun TelaEscolher(onMotorista: () -> Unit, onLoja: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Paleta.Amarelo), contentAlignment = Alignment.Center) {
            Text("🛒", fontSize = 46.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Loja no Carro", style = MaterialTheme.typography.headlineLarge)
        Text("Compre no banco de trás, pague com Pix.", color = Paleta.TextoSuave, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        BotaoPapel("Sou o motorista", "Recebo os pedidos e configuro a loja", destaque = true, onClick = onMotorista)
        Spacer(Modifier.height(14.dp))
        BotaoPapel("Esta é a loja", "O passageiro escolhe e paga neste aparelho", destaque = false, onClick = onLoja)
    }
}

@Composable
private fun BotaoPapel(titulo: String, subtitulo: String, destaque: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma)
            .background(if (destaque) Paleta.Amarelo else Paleta.Painel)
            .then(if (destaque) Modifier else Modifier.border(1.dp, Paleta.Linha, forma))
            .clickable(onClick = onClick).padding(22.dp)
    ) {
        Text(titulo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (destaque) Paleta.Noite else Paleta.Texto)
        Spacer(Modifier.height(4.dp))
        Text(subtitulo, fontSize = 14.sp, color = if (destaque) Paleta.Noite.copy(alpha = 0.75f) else Paleta.TextoSuave)
    }
}

// ---------- Loja fechada (aparelho do passageiro) ----------
@Composable
fun TelaLojaFechada(estado: String, onTrocarFuncao: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Paleta.Painel), contentAlignment = Alignment.Center) {
            Text("🌙", fontSize = 46.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Loja fechada", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Quando o motorista abrir a loja, você poderá escolher seus produtos aqui.",
            color = Paleta.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp)
        )
        Spacer(Modifier.height(20.dp))
        StatusPill(estado)
        Spacer(Modifier.height(32.dp))
        Text(
            "Trocar função deste aparelho", color = Paleta.TextoSuave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onTrocarFuncao).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

// ---------- Pagamento (aparelho do passageiro) ----------
@Composable
fun TelaPix(config: LojaConfig, pedido: Pedido, pago: Boolean, onVoltar: () -> Unit) {
    val codigo = remember(pedido.id) {
        PixBrCode.gerar(config.chavePix, config.nomeRecebedor, config.cidade, pedido.totalCentavos, pedido.id)
    }
    // Depois de pago, volta para a loja sozinho para o próximo passageiro.
    LaunchedEffect(pago) { if (pago) { delay(10_000); onVoltar() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pago) {
                Box(Modifier.size(128.dp).clip(CircleShape).background(Paleta.Verde), contentAlignment = Alignment.Center) {
                    Text("✓", color = Paleta.Noite, fontSize = 72.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(24.dp))
                Text("Pagamento confirmado", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("O motorista já vai te entregar.", color = Paleta.TextoSuave, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Box(
                    Modifier.clip(CircleShape).background(Paleta.Amarelo).clickable(onClick = onVoltar)
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                ) { Text("Novo pedido", color = Paleta.Noite, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            } else {
                Text(reais(pedido.totalCentavos), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                Text(
                    pedido.itens.joinToString { "${it.qtd}× ${it.nome}" },
                    color = Paleta.TextoSuave, textAlign = TextAlign.Center, fontSize = 15.sp
                )
                Spacer(Modifier.height(22.dp))
                // QR sempre em fundo branco para a câmera do banco ler bem.
                Box(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White).padding(18.dp)) {
                    Image(remember(codigo) { qr(codigo).asImageBitmap() }, "QR Code do Pix", Modifier.size(250.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "Abra o app do seu banco, escolha Pix e leia o QR Code.",
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp)
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Paleta.Amarelo, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Aguardando o pagamento", color = Paleta.TextoSuave)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Cancelar pedido", color = Paleta.TextoSuave, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onVoltar).padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
        }
    }
}

// ---------- Motorista ----------
@Composable
fun TelaMotorista(
    estado: String,
    pedidos: List<Pedido>,
    lojaAberta: Boolean,
    onLojaAberta: (Boolean) -> Unit,
    mostrarSobreposicao: Boolean,
    onPermitirSobreposicao: () -> Unit,
    mostrarBateria: Boolean,
    onPermitirBateria: () -> Unit,
    onFaturamento: () -> Unit,
    onConfigurar: () -> Unit,
    onConfirmar: (Pedido) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pedidos", style = MaterialTheme.typography.headlineMedium)
                StatusPill(estado)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Faturamento", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).background(Paleta.Painel).clickable(onClick = onFaturamento)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
                Text(
                    "Configurar", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).background(Paleta.Painel).clickable(onClick = onConfigurar)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }

        CartaoLoja(lojaAberta, conectado = estado == "Conectado", onChange = onLojaAberta)
        if (mostrarSobreposicao) CartaoSobreposicao(onPermitirSobreposicao)
        if (mostrarBateria) CartaoBateria(onPermitirBateria)

        if (pedidos.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
                    Text("🛍️", fontSize = 56.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhum pedido ainda", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Quando um passageiro fizer um pedido, aparece um popup na tela, mesmo com outro app aberto.",
                        color = Paleta.TextoSuave, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(pedidos.reversed(), key = { it.id }) { p -> CardPedido(p, onConfirmar) }
            }
        }
    }
}

/** Abre e fecha a loja: aberta = tablet travado na tela da loja; fechada = tablet liberado. */
@Composable
private fun CartaoLoja(aberta: Boolean, conectado: Boolean, onChange: (Boolean) -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(forma)
            .background(if (aberta) Paleta.Verde.copy(alpha = 0.16f) else Paleta.Painel)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (aberta) "Loja aberta" else "Loja fechada", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(
                if (aberta) "O tablet está travado na loja. Só você destrava."
                else "O tablet está liberado. Abra a loja quando o passageiro entrar.",
                color = Paleta.TextoSuave, fontSize = 14.sp
            )
            if (!conectado) {
                Text("Tablet desconectado: a mudança vale assim que ele conectar.", color = Paleta.Ambar, fontSize = 13.sp)
            }
        }
        Switch(checked = aberta, onCheckedChange = onChange)
    }
}

@Composable
private fun CartaoSobreposicao(onPermitir: () -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(forma)
            .background(Paleta.Ambar.copy(alpha = 0.14f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Para o popup do pedido aparecer por cima do Uber, permita \"Exibir sobre outros apps\".",
            Modifier.weight(1f).padding(end = 12.dp), fontSize = 14.sp
        )
        Box(
            Modifier.clip(CircleShape).background(Paleta.Ambar).clickable(onClick = onPermitir)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) { Text("Permitir", color = Paleta.Noite, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun CartaoBateria(onPermitir: () -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(forma)
            .background(Paleta.Ambar.copy(alpha = 0.14f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Para receber pedidos com o Uber na tela, deixe o app rodar em segundo plano.",
            Modifier.weight(1f).padding(end = 12.dp), fontSize = 14.sp
        )
        Box(
            Modifier.clip(CircleShape).background(Paleta.Ambar).clickable(onClick = onPermitir)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) { Text("Permitir", color = Paleta.Noite, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun CardPedido(p: Pedido, onConfirmar: (Pedido) -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    val hora = remember(p.criadoEm) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(p.criadoEm)) }
    Column(
        Modifier.fillMaxWidth().clip(forma).background(Paleta.Painel)
            .then(if (p.pago) Modifier else Modifier.border(2.dp, Paleta.Amarelo, forma))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pedido #${p.id}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(hora, color = Paleta.TextoSuave, fontSize = 14.sp)
            }
            Text(reais(p.totalCentavos), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Paleta.Amarelo)
        }
        Spacer(Modifier.height(12.dp))
        p.itens.forEach { Text("${it.qtd}× ${it.nome}", fontSize = 17.sp, modifier = Modifier.padding(vertical = 2.dp)) }
        Spacer(Modifier.height(14.dp))
        if (p.pago) {
            Text("Pago", color = Paleta.Verde, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        } else {
            Box(
                Modifier.fillMaxWidth().clip(CircleShape).background(Paleta.Amarelo).clickable { onConfirmar(p) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text("Confirmar Pix recebido", color = Paleta.Noite, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
        }
    }
}
