package com.lojanocarro

/**
 * Catálogo de exemplo para testar a experiência de compra.
 * Para usar fotos reais, coloque os arquivos em app/src/main/assets/produtos/
 * com estes nomes (jpg ou png, quadradas, ~600x600). Sem a foto, aparece o emoji sobre a cor do produto.
 * PNG com fundo transparente fica bonito sobre a cor de embalagem.
 */
object CatalogoFicticio {
    val produtos = listOf(
        Produto("bala", "Bala de goma", 100, "🍬", "produtos/bala.jpg", "Doces", "#FF7AA2", estoque = 20),
        Produto("refri", "Refrigerante lata 350ml", 500, "🥤", "produtos/refri.jpg", "Bebidas", "#FF5C4D", destaque = true, estoque = 20),
        Produto("choco", "Chocolate ao leite", 450, "🍫", "produtos/choco.jpg", "Doces", "#A9714B", estoque = 20),
        Produto("batata", "Batata chips", 600, "🥔", "produtos/batata.jpg", "Salgados", "#F7B32B", estoque = 20),
        Produto("agua", "Água mineral 500ml", 300, "💧", "produtos/agua.jpg", "Bebidas", "#4CC9F0", estoque = 20)
    )
}
