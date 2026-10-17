package com.lojanocarro

import java.text.Normalizer
import java.util.Locale

/** Gera o "Pix copia e cola" (BR Code estático) para exibir como QR Code. */
object PixBrCode {
    /** O BR Code só aceita ASCII: tira acentos ("São Paulo" -> "SAO PAULO") e deixa em maiúsculas, senão o CRC/banco rejeita. */
    private fun limpar(t: String): String =
        Normalizer.normalize(t.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .filter { it.code in 32..126 }
            .uppercase(Locale.ROOT)

    data class Dados(val chave: String, val nome: String, val cidade: String)

    private fun campos(s: String): Map<String, String>? {
        val m = LinkedHashMap<String, String>()
        var i = 0
        while (i < s.length) {
            if (i + 4 > s.length) return null
            val id = s.substring(i, i + 2)
            val n = s.substring(i + 2, i + 4).toIntOrNull() ?: return null
            if (i + 4 + n > s.length) return null
            m[id] = s.substring(i + 4, i + 4 + n)
            i += 4 + n
        }
        return m
    }

    /**
     * Se o texto for um "Pix copia e cola" inteiro (começa com 000201), tira dele a chave, o nome e a cidade.
     * Serve para quando o motorista cola o código todo no campo da chave.
     */
    fun extrair(texto: String): Dados? {
        val t = texto.trim()
        if (!t.startsWith("000201") || t.length < 40) return null
        val topo = campos(t) ?: return null
        val conta = campos(topo["26"] ?: return null) ?: return null
        val chave = conta["01"]?.takeIf { it.isNotBlank() } ?: return null
        return Dados(chave, topo["59"].orEmpty(), topo["60"].orEmpty())
    }

    fun gerar(chaveInformada: String, nomeRecebedor: String, cidadeRecebedor: String, centavos: Int, txid: String): String {
        // Se já foi salvo o código inteiro no lugar da chave, usa só a chave que está dentro dele.
        val chave = extrair(chaveInformada)?.chave ?: chaveInformada.trim()
        val nome = limpar(nomeRecebedor)
        val cidade = limpar(cidadeRecebedor)
        fun f(id: String, v: String) = id + v.length.toString().padStart(2, '0') + v
        val conta = f("00", "br.gov.bcb.pix") + f("01", chave)
        val valor = "%.2f".format(Locale.US, centavos / 100.0)
        val semCrc = f("00", "01") + f("26", conta) + f("52", "0000") + f("53", "986") +
            f("54", valor) + f("58", "BR") + f("59", nome.take(25)) + f("60", cidade.take(15)) +
            f("62", f("05", txid.filter { it.isLetterOrDigit() }.take(25).ifEmpty { "***" })) + "6304"
        return semCrc + crc16(semCrc)
    }

    private fun crc16(s: String): String {
        var crc = 0xFFFF
        for (b in s.toByteArray(Charsets.US_ASCII)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return "%04X".format(crc)
    }
}
