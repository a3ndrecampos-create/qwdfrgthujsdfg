package com.lojanocarro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class Periodo(val rotulo: String) {
    HOJE("Hoje"), SEMANA("7 dias"), MES("30 dias"), TUDO("Tudo")
}

/** Meia-noite de hoje, voltando [diasAtras] dias. */
private fun inicioDoDia(diasAtras: Int): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -diasAtras)
    }.timeInMillis

private data class LinhaProdutoVendido(val nome: String, val qtd: Int, val centavos: Int)

/** Faturamento do motorista: total, número de pedidos, ticket médio, mais vendidos e últimas vendas. */
@Composable
fun TelaFaturamento(vendas: List<Venda>, onVoltar: () -> Unit, onZerar: () -> Unit) {
    var periodo by remember { mutableStateOf(Periodo.HOJE) }
    var confirmando by remember { mutableStateOf(false) }

    val inicio = when (periodo) {
        Periodo.HOJE -> inicioDoDia(0)
        Periodo.SEMANA -> inicioDoDia(6)
        Periodo.MES -> inicioDoDia(29)
        Periodo.TUDO -> 0L
    }
    val filtradas = vendas.filter { it.pagoEm >= inicio }
    val total = filtradas.sumOf { it.totalCentavos }
    val qtdPedidos = filtradas.size
    val ticket = if (qtdPedidos > 0) total / qtdPedidos else 0

    val ranking = filtradas.flatMap { it.itens }
        .groupBy { it.nome }
        .map { (nome, itens) -> LinhaProdutoVendido(nome, itens.sumOf { it.qtd }, itens.sumOf { it.qtd * it.precoCentavos }) }
        .sortedByDescending { it.centavos }
    val formato = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "‹ Voltar", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onVoltar).padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("Faturamento", style = MaterialTheme.typography.headlineMedium) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Periodo.values().forEach { p ->
                        val sel = p == periodo
                        Text(
                            p.rotulo, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (sel) Paleta.Noite else Paleta.Texto,
                            modifier = Modifier.clip(CircleShape)
                                .background(if (sel) Paleta.Amarelo else Paleta.Painel)
                                .clickable { periodo = p }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paleta.Painel).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Total recebido", color = Paleta.TextoSuave, fontSize = 14.sp)
                    Text(reais(total), fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, color = Paleta.Verde)
                    Text(
                        "$qtdPedidos ${if (qtdPedidos == 1) "pedido" else "pedidos"}  •  ticket médio ${reais(ticket)}",
                        color = Paleta.TextoSuave, fontSize = 14.sp
                    )
                }
            }

            if (filtradas.isEmpty()) {
                item {
                    Text(
                        "Nenhuma venda neste período. As vendas entram aqui quando você confirma o Pix.",
                        color = Paleta.TextoSuave, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
            } else {
                item { Text("Mais vendidos", style = MaterialTheme.typography.titleLarge) }
                items(ranking.size) { i ->
                    val r = ranking[i]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paleta.Painel).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.nome, fontWeight = FontWeight.Bold)
                            Text("${r.qtd} ${if (r.qtd == 1) "unidade" else "unidades"}", color = Paleta.TextoSuave, fontSize = 13.sp)
                        }
                        Text(reais(r.centavos), fontWeight = FontWeight.ExtraBold, color = Paleta.Amarelo)
                    }
                }

                item { Text("Últimas vendas", style = MaterialTheme.typography.titleLarge) }
                val recentes = filtradas.sortedByDescending { it.pagoEm }.take(30)
                items(recentes.size) { i ->
                    val v = recentes[i]
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paleta.Painel).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(formato.format(Date(v.pagoEm)), color = Paleta.TextoSuave, fontSize = 13.sp)
                            Text(v.itens.joinToString(", ") { "${it.qtd}× ${it.nome}" }, fontSize = 14.sp)
                        }
                        Text(reais(v.totalCentavos), fontWeight = FontWeight.ExtraBold, color = Paleta.Verde)
                    }
                }
            }

            if (vendas.isNotEmpty()) {
                item {
                    if (!confirmando) {
                        Text(
                            "Zerar histórico", color = Paleta.Vermelho, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).clickable { confirmando = true }.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Apagar todas as vendas?", color = Paleta.TextoSuave, fontSize = 14.sp)
                            Text(
                                "Apagar", color = Paleta.Vermelho, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.clip(CircleShape).clickable { onZerar(); confirmando = false }.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                            Text(
                                "Cancelar", fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(CircleShape).clickable { confirmando = false }.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
