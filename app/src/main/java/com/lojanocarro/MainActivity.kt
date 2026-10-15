package com.lojanocarro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class Modo { ESCOLHER, LOJA, MOTORISTA, CONFIG }

class MainActivity : ComponentActivity() {
    private val permissoes = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lista = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) lista += listOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) lista += listOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS)
        permissoes.launch(lista.toTypedArray())

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("pedidos", "Pedidos", NotificationManager.IMPORTANCE_HIGH))

        setContent { MaterialTheme { Raiz() } }
    }

    private fun notificar(p: Pedido) {
        val texto = p.itens.joinToString { "${it.qtd}x ${it.nome}" }
        val n = NotificationCompat.Builder(this, "pedidos")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Novo pedido #${p.id} — ${reais(p.totalCentavos)}")
            .setContentText(texto).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(this).notify(p.id.hashCode(), n) } catch (_: SecurityException) {}
    }

    @Composable
    fun Raiz() {
        var modo by remember { mutableStateOf(Modo.ESCOLHER) }
        var estado by remember { mutableStateOf("") }
        var config by remember { mutableStateOf(LojaConfig("sua-chave-pix", "Seu Nome", "SAO PAULO")) }
        val pedidos = remember { mutableStateListOf<Pedido>() }
        var pagoId by remember { mutableStateOf<String?>(null) }
        var pareamento by remember { mutableStateOf<Pareamento?>(null) }

        val store = remember { ConfigStore(applicationContext) }
        val scope = rememberCoroutineScope()

        // Carrega a configuração salva (no celular do motorista; no tablet ela chega pelo Nearby).
        LaunchedEffect(Unit) { store.configFlow.first()?.let { config = it } }

        val link = remember {
            NearbyLink(this, onMensagem = { m ->
                when (m) {
                    is Mensagem.Config -> config = m.config
                    is Mensagem.NovoPedido -> { pedidos += m.pedido; notificar(m.pedido) }
                    is Mensagem.PagamentoConfirmado -> pagoId = m.pedidoId
                    is Mensagem.Token -> {} // consumido dentro do NearbyLink
                }
            }, onEstado = { estado = it }, onPareamento = { pareamento = it })
        }

        // Motorista: sempre que conectar ou salvar a configuração, manda a loja atualizada ao tablet.
        LaunchedEffect(estado, config, modo) {
            if ((modo == Modo.MOTORISTA || modo == Modo.CONFIG) && estado == "Conectado") {
                link.enviar(Mensagem.Config(config))
            }
        }

        when (modo) {
            Modo.ESCOLHER -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically)) {
                Text("Loja no Carro", style = MaterialTheme.typography.headlineLarge)
                Button({ modo = Modo.MOTORISTA; link.anunciarComoMotorista() }, Modifier.fillMaxWidth()) { Text("Sou o motorista") }
                Button({ modo = Modo.LOJA; link.procurarComoLoja() }, Modifier.fillMaxWidth()) { Text("Este aparelho é a loja (passageiro)") }
            }
            Modo.MOTORISTA -> Column(Modifier.fillMaxSize()) {
                TextButton({ modo = Modo.CONFIG }, Modifier.padding(horizontal = 8.dp)) { Text("⚙ Configurar loja") }
                Box(Modifier.weight(1f)) {
                    TelaMotorista(estado, pedidos) { p ->
                        val i = pedidos.indexOf(p); pedidos[i] = p.copy(pago = true)
                        link.enviar(Mensagem.PagamentoConfirmado(p.id))
                    }
                }
            }
            Modo.CONFIG -> Column(Modifier.fillMaxSize()) {
                TextButton({ modo = Modo.MOTORISTA }, Modifier.padding(horizontal = 8.dp)) { Text("← Voltar") }
                Box(Modifier.weight(1f)) {
                    TelaConfiguracao(config) { nova ->
                        config = nova
                        scope.launch { store.salvar(nova) }
                        modo = Modo.MOTORISTA
                    }
                }
            }
            Modo.LOJA -> Column(Modifier.fillMaxSize()) {
                if (estado != "Conectado") {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text("⚠ $estado — o pedido só chega ao motorista quando estiver conectado",
                            Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Box(Modifier.weight(1f)) {
                    TelaLojaTotem(config, pagoId, onFinalizar = { link.enviar(Mensagem.NovoPedido(it)) }, onNovoPedido = { pagoId = null })
                }
            }
        }

        pareamento?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Confirmar pareamento") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Os dois aparelhos devem mostrar este mesmo código:")
                        Text(p.digitos, style = MaterialTheme.typography.displayMedium)
                    }
                },
                confirmButton = { Button(onClick = p.aceitar) { Text("É o mesmo") } },
                dismissButton = { TextButton(onClick = p.recusar) { Text("Não é") } }
            )
        }
    }
}
