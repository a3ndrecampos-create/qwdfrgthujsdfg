package com.lojanocarro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import java.util.UUID

/**
 * Tela da loja no estilo totem: fotos grandes, toque no card adiciona, +/− direto no card,
 * barra fixa embaixo com total e botão "Pagar com Pix". Dois toques até o QR Code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaLojaTotem(
    config: LojaConfig,
    pedidoPago: String?,
    onFinalizar: (Pedido) -> Unit,
    onNovoPedido: () -> Unit
) {
    val carrinho = remember { mutableStateMapOf<String, Int>() }
    var pedido by remember { mutableStateOf<Pedido?>(null) }

    val atual = pedido
    if (atual != null) {
        TelaPix(config, atual, pago = pedidoPago == atual.id, onVoltar = {
            pedido = null; carrinho.clear(); onNovoPedido()
        })
        return
    }

    val qtdTotal = carrinho.values.sum()
    val total = config.produtos.sumOf { (carrinho[it.id] ?: 0) * it.precoCentavos }

    Scaffold(
        topBar = { TopAppBar(title = { Text("O que você quer hoje?", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            if (qtdTotal > 0) {
                Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("$qtdTotal ${if (qtdTotal == 1) "item" else "itens"}", fontSize = 14.sp)
                            Text(reais(total), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton({ carrinho.clear() }) { Text("Limpar") }
                        Button(
                            modifier = Modifier.height(64.dp),
                            onClick = {
                                val itens = config.produtos.filter { (carrinho[it.id] ?: 0) > 0 }
                                    .map { ItemPedido(it.id, it.nome, carrinho.getValue(it.id), it.precoCentavos) }
                                val ped = Pedido(UUID.randomUUID().toString().take(8), itens)
                                pedido = ped
                                onFinalizar(ped)
                            }
                        ) { Text("Pagar com Pix", fontSize = 20.sp) }
                    }
                }
            }
        }
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(config.produtos, key = { it.id }) { p ->
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
}

@Composable
private fun CardProduto(produto: Produto, qtd: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (qtd > 0) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.clickable(onClick = onAdd)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))) {
                SubcomposeAsyncImage(
                    model = produto.imagem?.let { "file:///android_asset/$it" },
                    contentDescription = produto.nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(produto.emoji, fontSize = 64.sp) } },
                    loading = { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(produto.emoji, fontSize = 64.sp) } }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(produto.nome, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2)
            Text(reais(produto.precoCentavos), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            if (qtd == 0) {
                FilledTonalButton(onAdd, Modifier.fillMaxWidth().height(48.dp)) { Text("Adicionar") }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledIconButton(onRemove, Modifier.size(48.dp), shape = CircleShape) { Text("−", fontSize = 24.sp) }
                    Text("$qtd", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    FilledIconButton(onAdd, Modifier.size(48.dp), shape = CircleShape) { Text("+", fontSize = 24.sp) }
                }
            }
        }
    }
}
