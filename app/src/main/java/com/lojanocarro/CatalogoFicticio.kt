package com.lojanocarro

/**
 * Catálogo de exemplo para testar a experiência de compra.
 * Para usar fotos reais, coloque os arquivos em app/src/main/assets/produtos/
 * com estes nomes (jpg ou png, quadradas, ~600x600). Sem a foto, aparece o emoji.
 */
object CatalogoFicticio {
    val produtos = listOf(
        Produto("bala", "Bala de goma", 100, "🍬", "produtos/bala.jpg"),
        Produto("refri", "Refrigerante lata 350ml", 500, "🥤", "produtos/refri.jpg"),
        Produto("choco", "Chocolate ao leite", 450, "🍫", "produtos/choco.jpg"),
        Produto("batata", "Batata chips", 600, "🥔", "produtos/batata.jpg"),
        Produto("agua", "Água mineral 500ml", 300, "💧", "produtos/agua.jpg")
    )
}
