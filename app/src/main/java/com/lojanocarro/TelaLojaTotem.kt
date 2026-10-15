package com.lojanocarro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.UUID

private const val TODAS = "Tudo"

/**
 * Loja do passageiro. Foto grande sobre a cor da embalagem, preço em amarelo, um toque para adicionar,
 * e uma barra amarela fixa embaixo com o total. Dois toques até o QR Code do Pix.
 */
@Composable
fun TelaLojaTotem(
    config: LojaConfig,
    estado: String,
    pedidoPago: String?,
    onFinalizar: (Pedido) -> Unit,
    onNovoPedido: () -> Unit
) {
    val carrinho = remember { mutableStateMapOf<String, Int>() }
    var pedido by remember { mutableStateOf<Pedido?>(null) }
    var categoria by remember { mutableStateOf(TODAS) }

    val atual = pedido
    if (atual != null) {
        TelaPix(config, atual, pago = pedidoPago == atual.id, onVoltar = {
            pedido = null; carrinho.clear(); onNovoPedido()
        })
        return
    }

    val categorias = remember(config.produtos) { listOf(TODAS) + config.produtos.map { it.categoria }.distinct() }
    val filtro = if (categoria in categorias) categoria else TODAS
    val visiveis = if (filtro == TODAS) config.produtos else config.produtos.filter { it.categoria == filtro }
    val qtdTotal = carrinho.values.sum()
    val total = config.produtos.sumOf { (carrinho[it.id] ?: 0) * it.precoCentavos }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("O que você quer hoje?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                StatusPill(estado)
            }

            if (categorias.size > 2) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categorias) { c ->
                        val sel = c == filtro
                        Box(
                            Modifier.clip(CircleShape)
                                .background(if (sel) Paleta.Amarelo else Paleta.Painel)
                                .clickable { categoria = c }
                                .padding(horizontal = 20.dp, vertical = 13.dp)
                        ) {
                            Text(c, color = if (sel) Paleta.Noite else Paleta.Texto, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(168.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = if (qtdTotal > 0) 132.dp else 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(visiveis, key = { it.id }) { p ->
                    CardProduto(
                        produto = p,
                        qtd = carrinho[p.id] ?: 0,
                        onAdd = { carrinho[p.id] = (carrinho[p.id] ?: 0) + 1 },
                        onRemove = {
                            val q = (carrinho[p.id] ?: 0) - 1
                            if (q <= 0) carrinho.remove(p.id) else carrinho[p.id] = q
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = qtdTotal > 0,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            BarraCarrinho(qtdTotal, total) {
                val itens = config.produtos.filter { (carrinho[it.id] ?: 0) > 0 }
                    .map { ItemPedido(it.id, it.nome, carrinho.getValue(it.id), it.precoCentavos) }
                val ped = Pedido(UUID.randomUUID().toString().take(8), itens)
                pedido = ped
                onFinalizar(ped)
            }
        }
    }
}

@Composable
private fun CardProduto(produto: Produto, qtd: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    val selecionado = qtd > 0
    val borda by animateColorAsState(if (selecionado) Paleta.Amarelo else Color.Transparent, label = "borda")
    val forma = RoundedCornerShape(26.dp)

    Column(
        Modifier.fillMaxWidth().clip(forma).background(Paleta.Painel)
            .border(2.dp, borda, forma).clickable(onClick = onAdd)
    ) {
        // O emoji fica por baixo; se a foto existir ela cobre o emoji, se não existir o emoji aparece.
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(produto.corFundo()), contentAlignment = Alignment.Center) {
            Text(produto.emoji, fontSize = 76.sp)
            AsyncImage(
                model = produto.imagem?.let { "file:///android_asset/$it" },
                contentDescription = produto.nome,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (selecionado) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp).clip(CircleShape).background(Paleta.Amarelo),
                    contentAlignment = Alignment.Center
                ) { Text("$qtd", color = Paleta.Noite, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                produto.nome, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(reais(produto.precoCentavos), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Paleta.Amarelo)
            Spacer(Modifier.height(10.dp))
            if (!selecionado) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(Paleta.PainelAlto).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) { Text("Adicionar", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(Paleta.Amarelo),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BotaoPasso("−", onRemove)
                    Text(
                        "$qtd", Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = Paleta.Noite, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
                    )
                    BotaoPasso("+", onAdd)
                }
            }
        }
    }
}

@Composable
private fun BotaoPasso(simbolo: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(simbolo, color = Paleta.Noite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BarraCarrinho(qtd: Int, total: Int, onPagar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(30.dp)).background(Paleta.Amarelo)
            .padding(start = 24.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$qtd ${if (qtd == 1) "item" else "itens"}",
                color = Paleta.Noite.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Text(reais(total), color = Paleta.Noite, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(
            Modifier.clip(CircleShape).background(Paleta.Noite).clickable(onClick = onPagar)
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) { Text("Pagar com Pix", color = Paleta.Amarelo, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
    }
}
