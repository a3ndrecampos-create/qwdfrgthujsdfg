package com.lojanocarro

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voz do próprio aparelho (Text-to-Speech do Android) em português do Brasil.
 * Funciona sem internet se a voz pt-BR estiver instalada (Configurações > Texto para fala).
 */
class Falador(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var pronto = false
    private var pendente: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val r = tts?.setLanguage(Locale.forLanguageTag("pt-BR"))
        pronto = r != null && r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        val fala = pendente
        pendente = null
        if (pronto && fala != null) falar(fala)
    }

    fun falar(texto: String) {
        val t = tts ?: return
        if (!pronto) { pendente = texto; return }
        t.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "loja-no-carro")
    }

    fun parar() { tts?.stop() }

    fun liberar() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

/** Frases faladas. "unidades de" evita errar o plural e o gênero dos nomes dos produtos. */
object Falas {
    fun valor(centavos: Int): String {
        val r = centavos / 100
        val c = centavos % 100
        return when {
            r == 0 -> "$c centavos"
            c == 0 -> if (r == 1) "1 real" else "$r reais"
            else -> (if (r == 1) "1 real" else "$r reais") + " e $c centavos"
        }
    }

    fun pedido(p: Pedido): String {
        val itens = p.itens.joinToString(". ") {
            "${it.qtd} ${if (it.qtd == 1) "unidade" else "unidades"} de ${it.nome}"
        }
        return "Pix confirmado. Entregar: $itens. Total: ${valor(p.totalCentavos)}."
    }
}
