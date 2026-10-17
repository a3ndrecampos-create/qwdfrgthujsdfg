package com.lojanocarro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Núcleo do app: guarda a conexão entre os aparelhos e todo o estado fora da tela.
 * Por isso a conexão continua viva quando o Uber está na frente, quando a tela gira ou quando a Activity é recriada.
 * A tela só observa este estado; o ServicoLoja mantém o processo vivo no celular do motorista.
 */
class LojaCore private constructor(private val app: Context) {

    companion object {
        const val CANAL_PEDIDOS = "pedidos"
        const val CANAL_SERVICO = "servico"
        const val ID_SERVICO = 1
        const val ACAO_CONFIRMAR = "com.lojanocarro.CONFIRMAR"
        const val ACAO_ENCERRAR = "com.lojanocarro.ENCERRAR"

        @Volatile private var instancia: LojaCore? = null

        fun get(ctx: Context): LojaCore =
            instancia ?: synchronized(this) {
                instancia ?: LojaCore(ctx.applicationContext).also { instancia = it }
            }
    }

    private val prefs = app.getSharedPreferences("loja_nc", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = ConfigStore(app)
    private val receptor = ReceptorFotos(app)
    private val falador = Falador(app)

    /** "motorista" ou "loja": função deste aparelho, lembrada para voltar sozinho depois de fechar e abrir o app. */
    private var papel: String? = prefs.getString("papel", null)

    var modo by mutableStateOf(Modo.ESCOLHER)
        private set
    var estado by mutableStateOf("")
        private set
    var config by mutableStateOf(LojaConfig("sua-chave-pix", "Seu Nome", "SAO PAULO"))
        private set
    val pedidos = mutableStateListOf<Pedido>()
    var pagoId by mutableStateOf<String?>(null)
        private set
    var pareamento by mutableStateOf<Pareamento?>(null)
        private set
    var fotosVersao by mutableIntStateOf(0)
        private set
    var lojaAberta by mutableStateOf(prefs.getBoolean("loja_aberta", false))
        private set

    private val link = NearbyLink(
        app,
        onMensagem = { m -> aoReceber(m) },
        onEstado = { e ->
            estado = e
            if (e == "Conectado") aoConectar()
        },
        onPareamento = { pareamento = it }
    )

    init {
        criarCanais()
        // Config salva: no celular do motorista é a que ele editou; no tablet, a última que recebeu.
        scope.launch { store.configFlow.first()?.let { config = it } }
    }

    // ---------- função do aparelho ----------

    fun entrarComoMotorista() {
        papel = "motorista"
        prefs.edit().putString("papel", papel).apply()
        modo = Modo.MOTORISTA
        link.anunciarComoMotorista()
        iniciarServico()
    }

    fun entrarComoLoja() {
        papel = "loja"
        prefs.edit().putString("papel", papel).apply()
        modo = Modo.LOJA
        link.procurarComoLoja()
    }

    /** Volta sozinho para a função anterior (chamado ao abrir o app e quando o serviço reinicia). */
    fun retomar() {
        if (modo != Modo.ESCOLHER) return
        when (papel) {
            "motorista" -> entrarComoMotorista()
            "loja" -> entrarComoLoja()
        }
    }

    /** Desliga a conexão e volta à primeira tela. */
    fun sair() {
        link.encerrar()
        pararServico()
        papel = null
        prefs.edit().remove("papel").apply()
        modo = Modo.ESCOLHER
        estado = ""
    }

    fun mostrarConfig(mostrar: Boolean) {
        modo = if (mostrar) Modo.CONFIG else Modo.MOTORISTA
    }

    // ---------- loja aberta / fechada ----------

    /** Aberta = tablet travado na loja. Fechada = tablet liberado. */
    fun definirLoja(aberta: Boolean, avisar: Boolean = true) {
        lojaAberta = aberta
        prefs.edit().putBoolean("loja_aberta", aberta).apply()
        if (avisar) link.enviar(Mensagem.Loja(aberta))
    }

    /** Saída de emergência no tablet. Só funciona se o motorista definiu um PIN. */
    fun destravarComPin(pin: String): Boolean {
        val certo = config.pinLoja
        if (certo.isBlank() || pin != certo) return false
        definirLoja(false) // avisa o celular do motorista que a loja foi fechada
        scope.launch {
            delay(800) // dá tempo da mensagem sair antes de desligar a conexão
            sair()
        }
        return true
    }

    // ---------- pedidos ----------

    fun enviarPedido(p: Pedido) = link.enviar(Mensagem.NovoPedido(p))

    fun limparPago() { pagoId = null }

    fun confirmarPedido(id: String) {
        val i = pedidos.indexOfFirst { it.id == id }
        if (i < 0 || pedidos[i].pago) return
        val p = pedidos[i].copy(pago = true)
        pedidos[i] = p
        link.enviar(Mensagem.PagamentoConfirmado(id))
        if (config.falarPedido) falador.falar(Falas.pedido(p))
        NotificationManagerCompat.from(app).cancel(id.hashCode())
    }

    // ---------- configuração ----------

    fun salvarConfig(nova: LojaConfig) {
        config = nova
        scope.launch(Dispatchers.IO) {
            store.salvar(nova)
            Fotos.limparNaoUsadas(app, nova.produtos)
        }
        if (papel == "motorista" && estado == "Conectado") link.enviar(Mensagem.Config(nova))
    }

    // ---------- voz ----------

    fun falar(texto: String) = falador.falar(texto)

    fun pararFala() = falador.parar()

    // ---------- mensagens ----------

    private fun aoConectar() {
        if (papel == "motorista") {
            link.enviar(Mensagem.Config(config))
            link.enviar(Mensagem.Loja(lojaAberta))
        }
    }

    private fun aoReceber(m: Mensagem) {
        when (m) {
            is Mensagem.Config -> {
                config = m.config
                scope.launch(Dispatchers.IO) { store.salvar(m.config) }
                // Tablet: pede as fotos que ainda não tem e apaga as que a loja não usa mais.
                val faltando = Fotos.faltantes(app, m.config.produtos)
                if (faltando.isNotEmpty()) link.enviar(Mensagem.PedirFotos(faltando))
                Fotos.limparNaoUsadas(app, m.config.produtos)
            }
            is Mensagem.NovoPedido -> {
                pedidos += m.pedido
                notificarPedido(m.pedido)
            }
            is Mensagem.PagamentoConfirmado -> pagoId = m.pedidoId
            is Mensagem.Token -> {} // consumido dentro do NearbyLink
            is Mensagem.Loja -> definirLoja(m.aberta, avisar = false)
            // Motorista: manda, em pedaços, as fotos que o tablet pediu.
            is Mensagem.PedirFotos -> scope.launch(Dispatchers.IO) {
                m.nomes.forEach { nome -> Fotos.partes(app, nome).forEach { link.enviar(it) } }
            }
            // Tablet: junta os pedaços; quando a foto fecha, avisa os cards.
            is Mensagem.FotoParte -> { if (receptor.receber(m)) fotosVersao++ }
        }
    }

    // ---------- notificações ----------

    private fun criarCanais() {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_PEDIDOS, "Pedidos", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_SERVICO, "Loja em segundo plano", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun abrirApp(codigo: Int): PendingIntent = PendingIntent.getActivity(
        app, codigo,
        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Popup do pedido: aparece por cima de qualquer app (inclusive o do Uber) e tem o botão de confirmar o Pix. */
    private fun notificarPedido(p: Pedido) {
        val texto = p.itens.joinToString { "${it.qtd}x ${it.nome}" }
        val confirmar = PendingIntent.getBroadcast(
            app, p.id.hashCode(),
            Intent(app, AcaoReceiver::class.java).setAction(ACAO_CONFIRMAR).putExtra("id", p.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, CANAL_PEDIDOS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Novo pedido — ${reais(p.totalCentavos)}")
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(abrirApp(0))
            .addAction(0, "Confirmar Pix recebido", confirmar)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(app).notify(p.id.hashCode(), n) } catch (_: SecurityException) {}
    }

    fun notificacaoServico(): Notification {
        val encerrar = PendingIntent.getBroadcast(
            app, 2,
            Intent(app, AcaoReceiver::class.java).setAction(ACAO_ENCERRAR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(app, CANAL_SERVICO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Loja no Carro")
            .setContentText("Recebendo pedidos em segundo plano")
            .setContentIntent(abrirApp(1))
            .setOngoing(true)
            .addAction(0, "Encerrar", encerrar)
            .build()
    }

    private fun iniciarServico() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, ServicoLoja::class.java))
        } catch (_: Exception) {}
    }

    private fun pararServico() {
        app.stopService(Intent(app, ServicoLoja::class.java))
    }
}

/** Mantém o app vivo no celular do motorista enquanto o Uber está na tela. */
class ServicoLoja : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val core = LojaCore.get(this)
        try {
            val n = core.notificacaoServico()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(LojaCore.ID_SERVICO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(LojaCore.ID_SERVICO, n)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        core.retomar()
        return START_STICKY
    }
}

/** Botões das notificações: "Confirmar Pix recebido" e "Encerrar". */
class AcaoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val core = LojaCore.get(context)
        when (intent.action) {
            LojaCore.ACAO_CONFIRMAR -> intent.getStringExtra("id")?.let { core.confirmarPedido(it) }
            LojaCore.ACAO_ENCERRAR -> core.sair()
        }
    }
}
