package com.lojanocarro

import kotlinx.serialization.Serializable

@Serializable
data class Produto(
    val id: String,
    val nome: String,
    val precoCentavos: Int,
    val emoji: String = "🛒",
    /** Caminho dentro de app/src/main/assets, ex.: "produtos/bala.jpg". Se faltar, mostra o emoji. */
    val imagem: String? = null,
    /** Agrupa os produtos nos filtros do topo da loja (ex.: Doces, Bebidas). */
    val categoria: String = "Outros",
    /** Cor de embalagem (hex) que fica atrás da foto. */
    val cor: String = "#2E3F5F",
    /** Aparece no banner do topo da loja como sugestão do motorista. */
    val destaque: Boolean = false
)

@Serializable
data class ItemPedido(val produtoId: String, val nome: String, val qtd: Int, val precoCentavos: Int)

@Serializable
data class Pedido(
    val id: String,
    val itens: List<ItemPedido>,
    val criadoEm: Long = System.currentTimeMillis(),
    val pago: Boolean = false
) {
    val totalCentavos get() = itens.sumOf { it.qtd * it.precoCentavos }
}

/** Configurada pelo motorista (modo Motorista) e enviada ao tablet no pareamento. */
@Serializable
data class LojaConfig(
    val chavePix: String,
    val nomeRecebedor: String,
    val cidade: String,
    val produtos: List<Produto> = CatalogoFicticio.produtos,
    /** Tablet explica o tutorial em voz alta (ou liga o som do vídeo) enquanto ninguém está comprando. */
    val narrarTutorial: Boolean = false,
    /** Celular do motorista fala os itens do pedido quando ele confirma o Pix. */
    val falarPedido: Boolean = true,
    /** PIN de emergência para destravar o tablet direto nele (vazio = só o celular do motorista destrava). */
    val pinLoja: String = "",
    /** PIN para destravar o aparelho da loja sem o celular do motorista. Vazio = só o motorista destrava. */
    val pin: String = ""
)

/** Mensagens trocadas entre tablet e celular do motorista. */
@Serializable
sealed class Mensagem {
    @Serializable data class Config(val config: LojaConfig) : Mensagem()
    @Serializable data class NovoPedido(val pedido: Pedido) : Mensagem()
    @Serializable data class PagamentoConfirmado(val pedidoId: String) : Mensagem()
    /** Segredo de pareamento: o motorista entrega ao tablet no 1º pareamento. Tratado dentro do NearbyLink. */
    @Serializable data class Token(val valor: String) : Mensagem()
    /** Loja aberta (tablet travado) ou fechada (tablet liberado). Motorista -> tablet; o tablet avisa de volta ao destravar por PIN. */
    @Serializable data class Loja(val aberta: Boolean) : Mensagem()
    /** Tablet -> motorista: "me mande estas fotos, que eu ainda não tenho". */
    @Serializable data class PedirFotos(val nomes: List<String>) : Mensagem()
    /** Motorista -> tablet: um pedaço de uma foto (base64). O Nearby limita cada mensagem a 32 KB. */
    @Serializable data class FotoParte(val nome: String, val indice: Int, val total: Int, val dados: String) : Mensagem()
}
