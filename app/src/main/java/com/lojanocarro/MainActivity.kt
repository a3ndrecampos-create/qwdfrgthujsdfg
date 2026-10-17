package com.lojanocarro

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

enum class Modo { ESCOLHER, LOJA, MOTORISTA, CONFIG }

class MainActivity : ComponentActivity() {
    private val permissoes = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    /** Muda quando o app volta à tela, para reler se a permissão de segundo plano já foi dada. */
    private var bateriaVersao by mutableIntStateOf(0)
    private var jaTravou = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tela inteira, com as barras do sistema transparentes e ícones claros sobre o fundo azul-noite.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val lista = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) lista += listOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) lista += listOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS)
        permissoes.launch(lista.toTypedArray())

        // O núcleo vive fora da tela: a conexão continua com o Uber na frente e depois de girar a tela.
        val core = LojaCore.get(applicationContext)
        core.retomar() // volta sozinho à função anterior (motorista ou loja)

        setContent { LojaTheme { Raiz(core) } }
    }

    override fun onResume() {
        super.onResume()
        bateriaVersao++
    }

    // ---------- segundo plano ----------

    private fun bateriaLiberada(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun pedirSobreposicao() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) } catch (_: Exception) {}
        }
    }

    private fun pedirIgnorarBateria() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    // ---------- trava do tablet ----------

    /** Tela cheia sem barras e app fixado (screen pinning): sem botão Home/Recentes para o passageiro. */
    private fun entrarQuiosque() {
        jaTravou = true
        val ctrl = WindowCompat.getInsetsController(window, window.decorView)
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        try { startLockTask() } catch (_: Exception) {}
    }

    private fun sairQuiosque() {
        if (!jaTravou) return
        jaTravou = false
        try { stopLockTask() } catch (_: Exception) {}
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    @Composable
    fun Raiz(core: LojaCore) {
        // Com a loja aberta, o aparelho da loja fica travado. Só o celular do motorista (ou o PIN) destrava.
        val travar = core.modo == Modo.LOJA && core.lojaAberta

        // O tablet da loja não deixa a tela apagar, senão o tutorial de descanso nunca apareceria.
        val view = LocalView.current
        DisposableEffect(core.modo) {
            view.keepScreenOn = core.modo == Modo.LOJA
            onDispose { view.keepScreenOn = false }
        }

        DisposableEffect(travar) {
            if (travar) entrarQuiosque() else sairQuiosque()
            onDispose { }
        }
        BackHandler(enabled = travar) { /* o botão voltar não faz nada com a loja aberta */ }

        val bateriaOk = remember(bateriaVersao) { bateriaLiberada() }
        val sobreposicaoOk = remember(bateriaVersao) { core.sobreposicaoLiberada() }

        // Saída de emergência: 7 toques seguidos no canto superior esquerdo pedem o PIN.
        var toques by remember { mutableIntStateOf(0) }
        var ultimoToque by remember { mutableLongStateOf(0L) }
        var pedindoPin by remember { mutableStateOf(false) }

        CompositionLocalProvider(LocalFotosVersao provides core.fotosVersao) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // systemBarsPadding mantém o conteúdo longe das barras do sistema (quando elas estão visíveis).
                Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    when (core.modo) {
                        Modo.ESCOLHER -> TelaEscolher(
                            onMotorista = { core.entrarComoMotorista() },
                            onLoja = { core.entrarComoLoja() }
                        )
                        Modo.MOTORISTA -> TelaMotorista(
                            estado = core.estado,
                            pedidos = core.pedidos,
                            lojaAberta = core.lojaAberta,
                            onLojaAberta = { core.definirLoja(it) },
                            mostrarSobreposicao = !sobreposicaoOk,
                            onPermitirSobreposicao = { pedirSobreposicao() },
                            mostrarBateria = !bateriaOk,
                            onPermitirBateria = { pedirIgnorarBateria() },
                            onConfigurar = { core.mostrarConfig(true) },
                            onConfirmar = { core.confirmarPedido(it.id) }
                        )
                        Modo.CONFIG -> TelaConfiguracao(
                            core.config,
                            onVoltar = { core.mostrarConfig(false) },
                            onSalvar = { nova ->
                                core.salvarConfig(nova)
                                core.mostrarConfig(false)
                            },
                            onTrocarFuncao = { core.sair() }
                        )
                        Modo.LOJA -> if (core.lojaAberta) {
                            TelaLojaTotem(
                                core.config, core.estado, core.pagoId,
                                onFinalizar = { core.enviarPedido(it) },
                                onNovoPedido = { core.limparPago() },
                                onFalar = { core.falar(it) },
                                onPararFala = { core.pararFala() }
                            )
                        } else {
                            TelaLojaFechada(core.estado, onTrocarFuncao = { core.sair() })
                        }
                    }

                    if (travar) {
                        Box(
                            Modifier.align(Alignment.TopStart).size(72.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val agora = System.currentTimeMillis()
                                toques = if (agora - ultimoToque < 2_000) toques + 1 else 1
                                ultimoToque = agora
                                if (toques >= 7) {
                                    toques = 0
                                    pedindoPin = true
                                }
                            }
                        )
                    }
                }
            }
        }

        if (pedindoPin && travar) {
            DialogoPin(
                onTentar = { pin -> core.destravarComPin(pin) },
                onFechar = { pedindoPin = false }
            )
        }

        core.pareamento?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Confirmar pareamento") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Os dois aparelhos devem mostrar este mesmo código.")
                        Text(p.digitos, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
                    }
                },
                confirmButton = { Button(onClick = p.aceitar) { Text("É o mesmo") } },
                dismissButton = { TextButton(onClick = p.recusar) { Text("Não é") } }
            )
        }
    }
}

/** PIN de emergência do tablet. Depois de 5 erros, espera 60 segundos. */
@Composable
private fun DialogoPin(onTentar: (String) -> Boolean, onFechar: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var falhas by remember { mutableIntStateOf(0) }
    var bloqueadoAte by remember { mutableLongStateOf(0L) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Destravar o tablet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Digite o PIN que o motorista definiu na configuração da loja.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { c -> c.isDigit() }.take(8) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = erro != null
                )
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val agora = System.currentTimeMillis()
                if (agora < bloqueadoAte) {
                    erro = "Espere ${(bloqueadoAte - agora) / 1000 + 1}s para tentar de novo."
                } else if (onTentar(pin)) {
                    onFechar()
                } else {
                    falhas++
                    pin = ""
                    if (falhas >= 5) {
                        bloqueadoAte = agora + 60_000
                        falhas = 0
                        erro = "Muitas tentativas. Espere 60s."
                    } else {
                        erro = "PIN incorreto."
                    }
                }
            }) { Text("Destravar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}
