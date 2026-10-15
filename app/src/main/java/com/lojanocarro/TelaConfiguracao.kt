package com.lojanocarro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Tela do motorista: chave Pix, nome, cidade e produtos/preços. Ao salvar, persiste e envia ao tablet. */
@Composable
fun TelaConfiguracao(configAtual: LojaConfig, onSalvar: (LojaConfig) -> Unit) {
    var chavePix by remember { mutableStateOf(configAtual.chavePix) }
    var nome by remember { mutableStateOf(configAtual.nomeRecebedor) }
    var cidade by remember { mutableStateOf(configAtual.cidade) }
    val produtos = remember { mutableStateListOf(*configAtual.produtos.toTypedArray()) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Configurar loja", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(chavePix, { chavePix = it }, label = { Text("Chave Pix") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nome, { nome = it }, label = { Text("Nome do recebedor") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(cidade, { cidade = it }, label = { Text("Cidade") }, modifier = Modifier.fillMaxWidth())

        Text("Produtos", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(produtos) { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p.nome, { produtos[i] = p.copy(nome = it) },
                        label = { Text("Nome") }, modifier = Modifier.weight(1f))
                    OutlinedTextField("%.2f".format(p.precoCentavos / 100.0),
                        { v -> v.replace(',', '.').toDoubleOrNull()?.let { produtos[i] = p.copy(precoCentavos = Math.round(it * 100).toInt()) } },
                        label = { Text("R$") }, modifier = Modifier.width(100.dp))
                    TextButton({ produtos.removeAt(i) }) { Text("Remover") }
                }
            }
            item {
                TextButton({ produtos += Produto("item${System.currentTimeMillis()}", "Novo item", 100) }) { Text("+ Adicionar produto") }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = chavePix.isNotBlank() && nome.isNotBlank() && cidade.isNotBlank() && produtos.isNotEmpty(),
            onClick = { onSalvar(LojaConfig(chavePix.trim(), nome.trim(), cidade.trim(), produtos.toList())) }
        ) { Text("Salvar e enviar para o tablet") }
    }
}
