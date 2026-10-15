package com.lojanocarro

import kotlinx.serialization.Serializable

@Serializable
data class Produto(
    val id: String,
    val nome: String,
    val precoCentavos: Int,
    val emoji: String = "🛒",
    /** Caminho dentro de app/src/main/assets, ex.: "produtos/bala.jpg". Se faltar, mostra o emoji. */
    val imagem: String? = null
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
    val produtos: List<Produto> = CatalogoFicticio.produtos
)

/** Mensagens trocadas entre tablet e celular do motorista. */
@Serializable
sealed class Mensagem {
    @Serializable data class Config(val config: LojaConfig) : Mensagem()
    @Serializable data class NovoPedido(val pedido: Pedido) : Mensagem()
    @Serializable data class PagamentoConfirmado(val pedidoId: String) : Mensagem()
    /** Segredo de pareamento: o motorista entrega ao tablet no 1º pareamento. Tratado dentro do NearbyLink. */
    @Serializable data class Token(val valor: String) : Mensagem()
}
