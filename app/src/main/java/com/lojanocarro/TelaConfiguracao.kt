package com.lojanocarro

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Tela do motorista: chave Pix, nome, cidade e produtos/preços. Ao salvar, persiste e envia ao tablet. */
@Composable
fun TelaConfiguracao(configAtual: LojaConfig, onVoltar: () -> Unit, onSalvar: (LojaConfig) -> Unit) {
    var chavePix by remember { mutableStateOf(configAtual.chavePix) }
    var nome by remember { mutableStateOf(configAtual.nomeRecebedor) }
    var cidade by remember { mutableStateOf(configAtual.cidade) }
    val produtos = remember { mutableStateListOf(*configAtual.produtos.toTypedArray()) }
    val pronto = chavePix.isNotBlank() && nome.isNotBlank() && cidade.isNotBlank() && produtos.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "‹ Voltar", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onVoltar).padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("Configurar loja", style = MaterialTheme.typography.headlineMedium) }

            item {
                Secao("Recebimento via Pix", "Esses dados geram o QR Code que o passageiro escaneia.") {
                    OutlinedTextField(chavePix, { chavePix = it }, label = { Text("Chave Pix") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nome, { nome = it }, label = { Text("Nome do recebedor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cidade, { cidade = it }, label = { Text("Cidade") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }

            item { Text("Produtos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp)) }

            itemsIndexed(produtos, key = { _, p -> p.id }) { i, p ->
                LinhaProduto(p, onChange = { produtos[i] = it }, onRemover = { produtos.removeAt(i) })
            }

            item {
                Text(
                    "+ Adicionar produto", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).background(Paleta.PainelAlto)
                        .clickable { produtos += Produto("item${System.currentTimeMillis()}", "Novo item", 100) }
                        .padding(vertical = 16.dp)
                )
            }
        }

        Box(Modifier.padding(20.dp)) {
            Button(
                onClick = { onSalvar(LojaConfig(chavePix.trim(), nome.trim(), cidade.trim(), produtos.toList())) },
                enabled = pronto,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text("Salvar e enviar ao tablet", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        }
    }
}

@Composable
private fun Secao(titulo: String, explicacao: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paleta.Painel).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(explicacao, color = Paleta.TextoSuave, fontSize = 14.sp)
        }
        conteudo()
    }
}

@Composable
private fun LinhaProduto(p: Produto, onChange: (Produto) -> Unit, onRemover: () -> Unit) {
    // O texto do preço fica num estado próprio para dar para digitar "4,5" sem o campo reformatar a cada tecla.
    var preco by remember(p.id) { mutableStateOf(String.format(Locale.US, "%.2f", p.precoCentavos / 100.0)) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paleta.Painel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                p.nome, { onChange(p.copy(nome = it)) },
                label = { Text("Nome") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                preco,
                { v ->
                    preco = v
                    v.replace(',', '.').toDoubleOrNull()?.let { onChange(p.copy(precoCentavos = Math.round(it * 100).toInt())) }
                },
                label = { Text("R$") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(110.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                p.categoria, { onChange(p.copy(categoria = it)) },
                label = { Text("Categoria") }, singleLine = true, modifier = Modifier.weight(1f)
            )
            Text(
                "Remover", color = Paleta.Vermelho, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onRemover).padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}
