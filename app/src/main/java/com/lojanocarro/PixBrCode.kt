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

    fun gerar(chave: String, nomeRecebedor: String, cidadeRecebedor: String, centavos: Int, txid: String): String {
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
