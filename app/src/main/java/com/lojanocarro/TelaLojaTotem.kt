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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.UUID

private const val TODAS = "Tudo"

/** Sem ninguém tocar por esse tempo, o carrinho é esvaziado e a tela de descanso (tutorial) volta. */
private const val OCIOSO_MS = 60_000L

/**
 * Loja do passageiro, no formato de totem de autoatendimento:
 * título e status no topo, sugestão do motorista em destaque, categorias na lateral esquerda,
 * grade de produtos e barra fixa de total embaixo.
 */
@Composable
fun TelaLojaTotem(
    config: LojaConfig,
    estado: String,
    pedidoPago: String?,
    onFinalizar: (Pedido) -> Unit,
    onNovoPedido: () -> Unit,
    onFalar: (String) -> Unit,
    onPararFala: () -> Unit
) {
    val carrinho = remember { mutableStateMapOf<String, Int>() }
    var pedido by remember { mutableStateOf<Pedido?>(null) }
    var categoria by remember { mutableStateOf(TODAS) }
    var descanso by remember { mutableStateOf(true) }
    var ultimoToque by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Vigia o tempo parado: esvazia o carrinho e volta ao tutorial.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val ocioso = System.currentTimeMillis() - ultimoToque > OCIOSO_MS
            if (ocioso && !descanso && pedido == null) {
                carrinho.clear()
                categoria = TODAS
                descanso = true
            }
        }
    }

    // Se o estoque mudou (o motorista editou ou outra venda baixou), tira do carrinho o que não existe mais.
    LaunchedEffect(config.produtos) {
        config.produtos.forEach { p ->
            val est = p.estoque
            val q = carrinho[p.id] ?: return@forEach
            if (est <= 0) carrinho.remove(p.id) else if (q > est) carrinho[p.id] = est
        }
    }

    val atual = pedido
    if (atual != null) {
        TelaPix(config, atual, pago = pedidoPago == atual.id, onVoltar = {
            pedido = null
            carrinho.clear()
            onNovoPedido()
            ultimoToque = System.currentTimeMillis()
        })
        return
    }

    val categorias = remember(config.produtos) { listOf(TODAS) + config.produtos.map { it.categoria }.distinct() }
    val filtro = if (categoria in categorias) categoria else TODAS
    val visiveis = if (filtro == TODAS) config.produtos else config.produtos.filter { it.categoria == filtro }
    val destaque = config.produtos.firstOrNull { it.destaque && it.estoque > 0 }
    val qtdTotal = carrinho.values.sum()
    val total = config.produtos.sumOf { (carrinho[it.id] ?: 0) * it.precoCentavos }

    fun adicionar(p: Produto) {
        val atual = carrinho[p.id] ?: 0
        if (atual >= p.estoque) return // sem estoque (ou já pegou tudo): não adiciona
        carrinho[p.id] = atual + 1
    }
    fun remover(p: Produto) {
        val q = (carrinho[p.id] ?: 0) - 1
        if (q <= 0) carrinho.remove(p.id) else carrinho[p.id] = q
    }
    fun finalizar() {
        val itens = config.produtos.filter { (carrinho[it.id] ?: 0) > 0 && it.estoque > 0 }
            .map { ItemPedido(it.id, it.nome, minOf(carrinho.getValue(it.id), it.estoque), it.precoCentavos) }
        if (itens.isEmpty()) { carrinho.clear(); return }
        val ped = Pedido(UUID.randomUUID().toString().take(8), itens)
        pedido = ped
        onFinalizar(ped)
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Só observa os toques (não consome) para saber que tem alguém usando.
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    ultimoToque = System.currentTimeMillis()
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("O que você quer hoje?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                StatusPill(estado)
            }

            if (destaque != null) BannerSugestao(destaque) { adicionar(destaque) }

            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (categorias.size > 2) {
                    TrilhoCategorias(
                        categorias = categorias,
                        atual = filtro,
                        iconeDe = { c ->
                            if (c == TODAS) "🛍️" else config.produtos.firstOrNull { it.categoria == c }?.emoji ?: "🛒"
                        },
                        onSelect = { categoria = it }
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = if (categorias.size > 2) 8.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visiveis, key = { it.id }) { p ->
                        CardProduto(
                            produto = p,
                            qtd = carrinho[p.id] ?: 0,
                            onAdd = { adicionar(p) },
                            onRemove = { remover(p) }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = qtdTotal > 0,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                BarraCarrinho(qtdTotal, total, onLimpar = { carrinho.clear() }, onPagar = { finalizar() })
            }
            if (qtdTotal == 0) {
                Text(
                    "Toque em um produto para começar",
                    color = Paleta.TextoSuave, textAlign = TextAlign.Center, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                )
            }
        }

        if (descanso) {
            TelaDescanso(config, onFalar, onPararFala) {
                descanso = false
                ultimoToque = System.currentTimeMillis()
            }
        }
    }
}

@Composable
private fun BannerSugestao(p: Produto, onAdd: () -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(forma).background(Paleta.Painel)
            .border(1.5.dp, Paleta.Amarelo.copy(alpha = 0.5f), forma)
            .clickable(onClick = onAdd).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(p.corFundo()),
            contentAlignment = Alignment.Center
        ) {
            Text(p.emoji, fontSize = 40.sp)
            AsyncImage(
                model = rememberModeloFoto(p),
                contentDescription = p.nome,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Sugestão do motorista", color = Paleta.TextoSuave, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(p.nome, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clip(CircleShape).background(Paleta.Amarelo).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(reais(p.precoCentavos), color = Paleta.Noite, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun TrilhoCategorias(
    categorias: List<String>,
    atual: String,
    iconeDe: (String) -> String,
    onSelect: (String) -> Unit
) {
    Column(
        Modifier.width(88.dp).fillMaxHeight().verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categorias.forEach { c ->
            val sel = c == atual
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(if (sel) Paleta.Amarelo else Paleta.Painel)
                    .clickable { onSelect(c) }.padding(vertical = 14.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(iconeDe(c), fontSize = 28.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    c, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (sel) Paleta.Noite else Paleta.Texto
                )
            }
        }
    }
}

@Composable
private fun CardProduto(produto: Produto, qtd: Int, onAdd: () -> Unit, onRemove: () -> Unit) {
    val selecionado = qtd > 0
    val esgotado = produto.estoque <= 0
    val noLimite = qtd >= produto.estoque
    val borda by animateColorAsState(if (selecionado) Paleta.Amarelo else Color.Transparent, label = "borda")
    val forma = RoundedCornerShape(22.dp)

    Column(
        Modifier.fillMaxWidth().then(if (esgotado) Modifier.alpha(0.45f) else Modifier)
            .clip(forma).background(Paleta.Painel)
            .border(2.dp, borda, forma).clickable(enabled = !esgotado && !noLimite, onClick = onAdd)
    ) {
        // O emoji fica por baixo; se a foto existir ela cobre o emoji, se não existir o emoji aparece.
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(produto.corFundo()), contentAlignment = Alignment.Center) {
            Text(produto.emoji, fontSize = 56.sp)
            AsyncImage(
                model = rememberModeloFoto(produto),
                contentDescription = produto.nome,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (selecionado) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(30.dp).clip(CircleShape).background(Paleta.Amarelo),
                    contentAlignment = Alignment.Center
                ) { Text("$qtd", color = Paleta.Noite, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                produto.nome, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp,
                minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(reais(produto.precoCentavos), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Paleta.Amarelo)
            Spacer(Modifier.height(8.dp))
            if (esgotado) {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(Paleta.Linha),
                    contentAlignment = Alignment.Center
                ) { Text("Indisponível", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Paleta.TextoSuave) }
            } else if (!selecionado) {
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(Paleta.PainelAlto).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) { Text("Adicionar", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(Paleta.Amarelo),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BotaoPasso("−", onRemove)
                    Text(
                        "$qtd", Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = Paleta.Noite, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp
                    )
                    BotaoPasso("+", onAdd, habilitado = !noLimite)
                }
            }
        }
    }
}

@Composable
private fun BotaoPasso(simbolo: String, onClick: () -> Unit, habilitado: Boolean = true) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).alpha(if (habilitado) 1f else 0.3f)
            .clickable(enabled = habilitado, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(simbolo, color = Paleta.Noite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BarraCarrinho(qtd: Int, total: Int, onLimpar: () -> Unit, onPagar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(30.dp)).background(Paleta.Amarelo)
            .padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "$qtd ${if (qtd == 1) "item" else "itens"}",
                color = Paleta.Noite.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            Text(reais(total), color = Paleta.Noite, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            "Limpar", color = Paleta.Noite, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onLimpar).padding(horizontal = 12.dp, vertical = 12.dp)
        )
        Box(
            Modifier.clip(CircleShape).background(Paleta.Noite).clickable(onClick = onPagar)
                .padding(horizontal = 20.dp, vertical = 17.dp)
        ) { Text("Pagar com Pix", color = Paleta.Amarelo, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
    }
}
