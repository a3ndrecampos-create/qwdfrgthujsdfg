package com.lojanocarro

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Direção visual: "noite na estrada". Fundo azul-noite (não cansa a vista no carro à noite e deixa as cores
 * dos produtos aparecerem) e amarelo de sinalização de táxi/conveniência só no que é ação ou preço.
 * Cada produto tem a própria cor de embalagem, que vira o fundo da foto.
 */
object Paleta {
    val Noite = Color(0xFF0E1726)       // fundo
    val Painel = Color(0xFF17233A)      // cards e campos
    val PainelAlto = Color(0xFF22314D)  // botões secundários
    val Linha = Color(0xFF2E3F5F)       // contornos
    val Texto = Color(0xFFF4F6FA)
    val TextoSuave = Color(0xFF9BA8C0)
    val Amarelo = Color(0xFFFFC83D)     // ação e preço
    val Verde = Color(0xFF3DDC97)       // pago / conectado
    val Ambar = Color(0xFFFFB020)       // atenção
    val Vermelho = Color(0xFFFF6B6B)
}

private val Esquema = darkColorScheme(
    primary = Paleta.Amarelo,
    onPrimary = Color(0xFF1A1400),
    primaryContainer = Paleta.PainelAlto,
    onPrimaryContainer = Paleta.Amarelo,
    secondary = Paleta.TextoSuave,
    onSecondary = Paleta.Noite,
    tertiary = Paleta.Verde,
    onTertiary = Color(0xFF00130A),
    background = Paleta.Noite,
    onBackground = Paleta.Texto,
    surface = Paleta.Painel,
    onSurface = Paleta.Texto,
    surfaceVariant = Paleta.PainelAlto,
    onSurfaceVariant = Paleta.TextoSuave,
    outline = Paleta.Linha,
    outlineVariant = Paleta.Linha,
    error = Paleta.Vermelho,
    onError = Paleta.Noite
)

private val Tipografia = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun LojaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, typography = Tipografia, content = content)
}

/** Cor de embalagem do produto, usada como fundo da foto. */
fun Produto.corFundo(): Color =
    runCatching { Color(android.graphics.Color.parseColor(cor)) }.getOrDefault(Paleta.PainelAlto)
