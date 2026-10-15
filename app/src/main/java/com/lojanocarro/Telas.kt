package com.lojanocarro

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID

fun reais(c: Int) = "R$ %d,%02d".format(c / 100, c % 100)

fun qr(texto: String, tam: Int = 600): Bitmap {
    val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tam, tam)
    return Bitmap.createBitmap(tam, tam, Bitmap.Config.RGB_565).also { bmp ->
        for (x in 0 until tam) for (y in 0 until tam) bmp.setPixel(x, y, if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    }
}

@Composable
fun TelaPix(config: LojaConfig, pedido: Pedido, pago: Boolean, onVoltar: () -> Unit) {
    val codigo = remember(pedido.id) { PixBrCode.gerar(config.chavePix, config.nomeRecebedor, config.cidade, pedido.totalCentavos, pedido.id) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        if (pago) {
            Text("✅ Pagamento confirmado!", style = MaterialTheme.typography.headlineMedium)
            Text("O motorista já vai te entregar.")
            Spacer(Modifier.height(16.dp)); Button(onVoltar) { Text("Novo pedido") }
        } else {
            Text("Pague ${reais(pedido.totalCentavos)} via Pix", style = MaterialTheme.typography.headlineSmall)
            Image(remember(codigo) { qr(codigo).asImageBitmap() }, "QR Code Pix", Modifier.size(280.dp))
            Text("Aguardando confirmação…")
            TextButton(onVoltar) { Text("Cancelar") }
        }
    }
}

// ---------- MODO MOTORISTA (celular) ----------
@Composable
fun TelaMotorista(estado: String, pedidos: List<Pedido>, onConfirmar: (Pedido) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pedidos • $estado", style = MaterialTheme.typography.titleLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pedidos.reversed()) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Pedido #${p.id} — ${reais(p.totalCentavos)}", style = MaterialTheme.typography.titleMedium)
                        p.itens.forEach { Text("${it.qtd}x ${it.nome}") }
                        if (p.pago) Text("✅ Pago") else Button({ onConfirmar(p) }) { Text("Confirmei o Pix") }
                    }
                }
            }
        }
    }
}
