package com.lojanocarro

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Pedido de confirmação de pareamento: os dois aparelhos mostram os mesmos dígitos. */
class Pareamento(val digitos: String, val aceitar: () -> Unit, val recusar: () -> Unit)

/**
 * Ligação tablet <-> celular do motorista via Nearby Connections (Bluetooth + Wi-Fi Direct, sem internet).
 *
 * - 1º pareamento: os dois aparelhos confirmam os dígitos; o motorista gera um segredo e entrega ao tablet.
 * - Depois disso os dois se reconhecem sozinhos (assinatura HMAC no nome, que muda a cada 5 min),
 *   então reconectam sem pedir confirmação, inclusive depois de reabrir o app.
 * - Se a conexão cair, o motorista volta a anunciar e o tablet volta a procurar, automaticamente.
 * - Mensagens enviadas sem conexão ficam numa fila e saem quando reconectar.
 */
class NearbyLink(
    context: Context,
    private val onMensagem: (Mensagem) -> Unit,
    private val onEstado: (String) -> Unit,
    private val onPareamento: (Pareamento?) -> Unit
) {
    private enum class Papel { MOTORISTA, LOJA }

    private val ctx = context.applicationContext
    private val client = Nearby.getConnectionsClient(ctx)
    private val prefs = ctx.getSharedPreferences("loja_nc", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val json = Json { classDiscriminator = "tipo" }
    private val serviceId = "com.lojanocarro.v1"

    private var papel: Papel? = null
    private var endpointId: String? = null
    private var token: String? = prefs.getString("token", null)
    private val pendentes = ArrayDeque<Mensagem>()

    // ---- identificação sem pedir dígitos de novo ----
    private fun bucket() = System.currentTimeMillis() / 300_000L

    private fun assinar(bucket: Long): String {
        val t = token ?: return ""
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(t.toByteArray(), "HmacSHA256")) }
        return mac.doFinal(bucket.toString().toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    }

    /** Nome anunciado: "m|assinatura" (motorista) ou "t|assinatura" (tablet). Sem segredo, nome simples. */
    private fun meuNome(prefixo: String) =
        if (token == null) (if (prefixo == "m") "motorista" else "loja") else "$prefixo|${assinar(bucket())}"

    private fun conhecido(nome: String, prefixo: String): Boolean {
        if (token == null || !nome.startsWith("$prefixo|")) return false
        val sig = nome.removePrefix("$prefixo|").toByteArray()
        return (-1L..1L).any { MessageDigest.isEqual(sig, assinar(bucket() + it).toByteArray()) }
    }

    // ---- callbacks Nearby ----
    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, p: Payload) {
            val bytes = p.asBytes() ?: return
            val m = json.decodeFromString<Mensagem>(String(bytes))
            if (m is Mensagem.Token) {
                if (papel == Papel.LOJA) { token = m.valor; prefs.edit().putString("token", m.valor).apply() }
            } else onMensagem(m)
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }

    private val conexao = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            val prefixoDoOutro = if (papel == Papel.MOTORISTA) "t" else "m"
            if (conhecido(info.endpointName, prefixoDoOutro)) {
                client.acceptConnection(id, payloads)          // aparelho já pareado: sem diálogo
                onEstado("Reconectando…")
            } else {
                onEstado("Confirme os dígitos")
                onPareamento(
                    Pareamento(
                        digitos = info.authenticationDigits,
                        aceitar = { client.acceptConnection(id, payloads); onPareamento(null); onEstado("Aguardando o outro aparelho…") },
                        recusar = { client.rejectConnection(id); onPareamento(null); onEstado("Pareamento recusado"); reiniciarEm(2000) }
                    )
                )
            }
        }

        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            onPareamento(null)
            if (r.status.isSuccess) {
                endpointId = id
                handler.removeCallbacksAndMessages(null)
                client.stopAdvertising(); client.stopDiscovery()
                onEstado("Conectado")
                if (papel == Papel.MOTORISTA) {
                    val t = token ?: UUID.randomUUID().toString().also { token = it; prefs.edit().putString("token", it).apply() }
                    enviarAgora(Mensagem.Token(t))
                }
                while (pendentes.isNotEmpty()) enviarAgora(pendentes.removeFirst())
            } else {
                onEstado("Falha ao conectar")
                reiniciarEm(2000)
            }
        }

        override fun onDisconnected(id: String) {
            endpointId = null
            onEstado("Desconectado — reconectando…")
            reiniciarEm(2000)
        }
    }

    // ---- ciclo de anúncio/busca ----
    private val renovarAnuncio = Runnable { if (endpointId == null) iniciar() }

    private fun reiniciarEm(ms: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (endpointId == null) iniciar() }, ms)
    }

    private fun iniciar() {
        when (papel) {
            Papel.MOTORISTA -> {
                client.stopAdvertising()
                client.startAdvertising(
                    meuNome("m"), serviceId, conexao,
                    AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
                )
                // O nome carrega uma assinatura que expira; renova antes de vencer.
                handler.postDelayed(renovarAnuncio, 240_000)
            }
            Papel.LOJA -> {
                client.stopDiscovery()
                val achou = object : EndpointDiscoveryCallback() {
                    override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                        client.requestConnection(meuNome("t"), id, conexao)
                    }
                    override fun onEndpointLost(id: String) {}
                }
                client.startDiscovery(serviceId, achou, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build())
            }
            null -> {}
        }
    }

    fun anunciarComoMotorista() { papel = Papel.MOTORISTA; iniciar(); onEstado("Aguardando tablet…") }

    fun procurarComoLoja() { papel = Papel.LOJA; iniciar(); onEstado("Procurando motorista…") }

    // ---- envio ----
    private fun enviarAgora(m: Mensagem) {
        val id = endpointId ?: return
        client.sendPayload(id, Payload.fromBytes(json.encodeToString(m).toByteArray()))
    }

    /** Sem conexão, guarda na fila (exceto Config, que é reenviada a cada conexão). */
    fun enviar(m: Mensagem) {
        if (endpointId != null) enviarAgora(m)
        else if (m !is Mensagem.Config && pendentes.size < 50) pendentes.addLast(m)
    }

    /** Esquece o segredo: tablets pareados antes passam a pedir confirmação de dígitos de novo. */
    fun esquecerTablets() { token = null; prefs.edit().remove("token").apply() }

    fun encerrar() {
        papel = null
        handler.removeCallbacksAndMessages(null)
        client.stopAllEndpoints(); client.stopAdvertising(); client.stopDiscovery()
    }
}
