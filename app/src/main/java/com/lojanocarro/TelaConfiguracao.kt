package com.lojanocarro

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Tela do motorista: chave Pix, voz, trava do tablet, nome, cidade e produtos (foto, preço, categoria). */
@Composable
fun TelaConfiguracao(
    configAtual: LojaConfig,
    onVoltar: () -> Unit,
    onSalvar: (LojaConfig) -> Unit,
    onTrocarFuncao: () -> Unit
) {
    var chavePix by remember { mutableStateOf(configAtual.chavePix) }
    var nome by remember { mutableStateOf(configAtual.nomeRecebedor) }
    var cidade by remember { mutableStateOf(configAtual.cidade) }
    var falarPedido by remember { mutableStateOf(configAtual.falarPedido) }
    var narrarTutorial by remember { mutableStateOf(configAtual.narrarTutorial) }
    var pin by remember { mutableStateOf(configAtual.pinLoja) }
    val produtos = remember { mutableStateListOf(*configAtual.produtos.toTypedArray()) }
    val pinOk = pin.isEmpty() || pin.length >= 4
    val pronto = chavePix.isNotBlank() && nome.isNotBlank() && cidade.isNotBlank() && produtos.isNotEmpty() && pinOk

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

            item {
                Secao(
                    "Trava do tablet",
                    "Com a loja aberta, o tablet fica travado na tela da loja e só o seu celular destrava."
                ) {
                    OutlinedTextField(
                        pin, { v -> pin = v.filter { c -> c.isDigit() }.take(8) },
                        label = { Text("PIN de emergência (4 a 8 números)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = !pinOk,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Se o seu celular descarregar, toque 7 vezes no canto superior esquerdo do tablet e digite este PIN. " +
                            "Sem PIN, só o celular destrava.",
                        color = Paleta.TextoSuave, fontSize = 14.sp
                    )
                }
            }

            item {
                Secao("Voz", "Opcional. Usa a voz do próprio aparelho, sem internet.") {
                    LinhaInterruptor(
                        "Falar os itens ao confirmar o Pix",
                        "No seu celular, para você ouvir o que entregar sem olhar a tela.",
                        falarPedido
                    ) { falarPedido = it }
                    LinhaInterruptor(
                        "Narrar o tutorial no tablet",
                        "Quando ninguém está comprando, o tablet explica em voz alta como pedir.",
                        narrarTutorial
                    ) { narrarTutorial = it }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Text("Produtos", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Toque na foto para escolher uma da galeria. Ela vai para o tablet sozinha quando os dois estiverem conectados.",
                        color = Paleta.TextoSuave, fontSize = 14.sp
                    )
                }
            }

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

            item {
                Text(
                    "Sair e escolher outra função", color = Paleta.TextoSuave, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable(onClick = onTrocarFuncao).padding(vertical = 14.dp)
                )
            }
        }

        Box(Modifier.padding(20.dp)) {
            Button(
                onClick = {
                    onSalvar(
                        LojaConfig(
                            chavePix.trim(), nome.trim(), cidade.trim(), produtos.toList(),
                            narrarTutorial = narrarTutorial, falarPedido = falarPedido, pinLoja = pin
                        )
                    )
                },
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
private fun LinhaInterruptor(titulo: String, detalhe: String, marcado: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titulo, fontWeight = FontWeight.SemiBold)
            Text(detalhe, color = Paleta.TextoSuave, fontSize = 14.sp)
        }
        Switch(checked = marcado, onCheckedChange = onChange)
    }
}

@Composable
private fun LinhaProduto(p: Produto, onChange: (Produto) -> Unit, onRemover: () -> Unit) {
    // O texto do preço fica num estado próprio para dar para digitar "4,5" sem o campo reformatar a cada tecla.
    var preco by remember(p.id) { mutableStateOf(String.format(Locale.US, "%.2f", p.precoCentavos / 100.0)) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pAtual by rememberUpdatedState(p)
    var salvando by remember { mutableStateOf(false) }
    var erroFoto by remember { mutableStateOf(false) }
    val modeloFoto = rememberModeloFoto(p)

    // Seletor de fotos do Android: não pede nenhuma permissão.
    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            salvando = true
            erroFoto = false
            scope.launch {
                val arquivo = withContext(Dispatchers.IO) { Fotos.salvarEscolhida(ctx, uri, pAtual.id) }
                salvando = false
                if (arquivo != null) onChange(pAtual.copy(imagem = Fotos.PREFIXO + arquivo)) else erroFoto = true
            }
        }
    }
    val escolherFoto = { seletor.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Paleta.Painel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(20.dp)).background(p.corFundo())
                    .clickable(onClick = { escolherFoto() }),
                contentAlignment = Alignment.Center
            ) {
                Text(p.emoji, fontSize = 38.sp)
                AsyncImage(
                    model = modeloFoto,
                    contentDescription = p.nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (modeloFoto != null) "Trocar foto" else "Escolher foto",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.clip(CircleShape).background(Paleta.PainelAlto)
                        .clickable(onClick = { escolherFoto() }).padding(horizontal = 18.dp, vertical = 12.dp)
                )
                if (p.imagem != null) {
                    Text(
                        "Remover foto", color = Paleta.TextoSuave, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.clip(CircleShape).clickable { onChange(p.copy(imagem = null)) }
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                }
                if (salvando) Text("Salvando foto…", color = Paleta.TextoSuave, fontSize = 13.sp)
                if (erroFoto) Text("Não deu para usar essa foto. Tente outra.", color = Paleta.Vermelho, fontSize = 13.sp)
            }
        }

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
