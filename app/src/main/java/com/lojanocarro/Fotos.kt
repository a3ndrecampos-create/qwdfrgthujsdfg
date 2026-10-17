package com.lojanocarro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

/** Muda quando chega uma foto nova no tablet, para os cards recarregarem a imagem. */
val LocalFotosVersao = compositionLocalOf { 0 }

/** Modelo de imagem para o Coil: arquivo salvo no aparelho, foto da pasta assets, ou null (mostra o emoji). */
@Composable
fun rememberModeloFoto(p: Produto): Any? {
    val ctx = LocalContext.current
    val versao = LocalFotosVersao.current
    return remember(p.imagem, versao) { Fotos.modelo(ctx, p) }
}

/**
 * Fotos dos produtos. A foto escolhida pelo motorista é recortada em quadrado, reduzida para 480 px e
 * salva em filesDir/fotos. Em Produto.imagem ela aparece como "foto:<nome do arquivo>".
 */
object Fotos {
    const val PREFIXO = "foto:"
    const val MAX_PARTES = 128
    private const val LADO = 480
    private const val TAMANHO_PARTE = 16 * 1024 // o Nearby aceita no máximo 32 KB por mensagem de bytes
    private val NOME_OK = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")

    fun nomeValido(nome: String) = NOME_OK.matches(nome)

    fun pasta(ctx: Context): File = File(ctx.filesDir, "fotos").also { it.mkdirs() }

    fun arquivo(ctx: Context, imagem: String): File? {
        if (!imagem.startsWith(PREFIXO)) return null
        val nome = imagem.removePrefix(PREFIXO)
        return if (nomeValido(nome)) File(pasta(ctx), nome) else null
    }

    fun modelo(ctx: Context, p: Produto): Any? {
        val img = p.imagem ?: return null
        if (img.startsWith(PREFIXO)) return arquivo(ctx, img)?.takeIf { it.exists() }
        val existe = runCatching { ctx.assets.open(img).close() }.isSuccess
        return if (existe) "file:///android_asset/$img" else null
    }

    /** Nomes das fotos que a config pede mas que ainda não estão neste aparelho. */
    fun faltantes(ctx: Context, produtos: List<Produto>): List<String> =
        produtos.mapNotNull { it.imagem }
            .filter { it.startsWith(PREFIXO) }
            .mapNotNull { img -> arquivo(ctx, img)?.takeIf { !it.exists() }?.name }
            .distinct()

    /** Apaga as fotos que nenhum produto usa mais. */
    fun limparNaoUsadas(ctx: Context, produtos: List<Produto>) {
        val usados = produtos.mapNotNull { it.imagem }
            .filter { it.startsWith(PREFIXO) }
            .map { it.removePrefix(PREFIXO) }
            .toSet()
        pasta(ctx).listFiles()?.forEach { f ->
            if (f.isFile && f.name !in usados && !f.name.endsWith(".part")) f.delete()
        }
    }

    /** Lê a foto escolhida na galeria, recorta, reduz e salva. Devolve o nome do arquivo ou null se falhar. */
    fun salvarEscolhida(ctx: Context, uri: Uri, produtoId: String): String? {
        try {
            val cr = ctx.contentResolver

            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
            if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

            var amostra = 1
            while (limites.outWidth / (amostra * 2) >= LADO && limites.outHeight / (amostra * 2) >= LADO) amostra *= 2
            val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
            val bruto = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) } ?: return null

            // Fotos tiradas com o celular guardam a rotação à parte; sem isto elas ficariam deitadas.
            val graus = runCatching {
                cr.openInputStream(uri)?.use { s ->
                    when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            }.getOrNull() ?: 0f

            val lado = minOf(bruto.width, bruto.height)
            val x = (bruto.width - lado) / 2
            val y = (bruto.height - lado) / 2
            val escala = minOf(1f, LADO.toFloat() / lado)
            val matriz = Matrix().apply { postRotate(graus); postScale(escala, escala) }
            val final = Bitmap.createBitmap(bruto, x, y, lado, lado, matriz, true)

            val png = final.hasAlpha() // mantém o fundo transparente de PNG de produto recortado
            val base = produtoId.filter { it.isLetterOrDigit() }.take(24).ifEmpty { "item" }
            val nome = "$base-${System.currentTimeMillis()}." + if (png) "png" else "jpg"
            FileOutputStream(File(pasta(ctx), nome)).use {
                final.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 82, it)
            }
            return nome
        } catch (e: Exception) {
            return null
        }
    }

    /** Divide a foto em pedaços para mandar pelo Nearby. */
    fun partes(ctx: Context, nome: String): List<Mensagem.FotoParte> {
        if (!nomeValido(nome)) return emptyList()
        val f = File(pasta(ctx), nome)
        if (!f.isFile) return emptyList()
        val bytes = f.readBytes()
        val total = (bytes.size + TAMANHO_PARTE - 1) / TAMANHO_PARTE
        if (total < 1 || total > MAX_PARTES) return emptyList()
        return (0 until total).map { i ->
            val ini = i * TAMANHO_PARTE
            val fim = minOf(bytes.size, ini + TAMANHO_PARTE)
            Mensagem.FotoParte(nome, i, total, Base64.encodeToString(bytes, ini, fim - ini, Base64.NO_WRAP))
        }
    }
}

/** Junta os pedaços que chegam do celular do motorista e grava a foto no tablet. */
class ReceptorFotos(private val ctx: Context) {
    private class Montagem(val total: Int) {
        val partes = arrayOfNulls<ByteArray>(total)
        var recebidas = 0
    }

    private val andamento = mutableMapOf<String, Montagem>()

    /** Devolve true quando a foto ficou completa e salva. */
    fun receber(m: Mensagem.FotoParte): Boolean {
        if (!Fotos.nomeValido(m.nome)) return false
        if (m.total < 1 || m.total > Fotos.MAX_PARTES || m.indice < 0 || m.indice >= m.total) return false
        try {
            val mt = andamento.getOrPut(m.nome) { Montagem(m.total) }
            if (mt.total != m.total) { andamento.remove(m.nome); return false }
            if (mt.partes[m.indice] == null) {
                mt.partes[m.indice] = Base64.decode(m.dados, Base64.NO_WRAP)
                mt.recebidas++
            }
            if (mt.recebidas < mt.total) return false

            andamento.remove(m.nome)
            val pasta = Fotos.pasta(ctx)
            val tmp = File(pasta, m.nome + ".part")
            FileOutputStream(tmp).use { out -> mt.partes.forEach { out.write(it!!) } }
            return tmp.renameTo(File(pasta, m.nome))
        } catch (e: Exception) {
            andamento.remove(m.nome)
            return false
        }
    }
}
