package com.lojanocarro

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Popup do pedido por cima de qualquer app (inclusive o do Uber).
 * Precisa da permissão "Exibir sobre outros apps"; sem ela, o app usa só a notificação.
 * Mostra um pedido por vez (o mais recente) com o botão de confirmar o Pix.
 */
class OverlayPedido(private val ctx: Context, private val aoConfirmar: (String) -> Unit) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var pedidoId: String? = null

    fun permitido(): Boolean = Settings.canDrawOverlays(ctx)

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    fun mostrar(p: Pedido) {
        if (!permitido()) return
        esconder()
        try {
            val caixa = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(Color.parseColor("#17233A"))
                    setStroke(dp(2), Color.parseColor("#FFC83D"))
                }
            }
            fun texto(t: String, sp: Float, cor: String, negrito: Boolean = false) = TextView(ctx).apply {
                text = t; textSize = sp; setTextColor(Color.parseColor(cor))
                if (negrito) setTypeface(typeface, Typeface.BOLD)
            }
            caixa.addView(texto("Novo pedido — ${reais(p.totalCentavos)}", 16f, "#FFC83D", true))
            caixa.addView(texto(p.itens.joinToString("\n") { "${it.qtd}× ${it.nome}" }, 14f, "#F4F6FA").apply {
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, dp(8))
            })
            fun botao(t: String, cor: String, corTexto: String, acao: () -> Unit) = TextView(ctx).apply {
                text = t; textSize = 13f; gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor(corTexto))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor(cor)) }
                setOnClickListener { acao() }
            }
            val botoes = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            botoes.addView(
                botao("Confirmar Pix", "#FFC83D", "#0E1726") { aoConfirmar(p.id) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            botoes.addView(
                botao("Fechar", "#22314D", "#F4F6FA") { esconder() },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { leftMargin = dp(8) }
            )
            caixa.addView(botoes)

            val lp = WindowManager.LayoutParams(
                dp(260), // estreito: não cobre a tela do Uber
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_FOCUSABLE: o toque vai para o popup, mas o app por trás continua normal.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(10)
                y = dp(60)
            }
            wm.addView(caixa, lp)
            view = caixa
            pedidoId = p.id
        } catch (_: Exception) {
            view = null
            pedidoId = null
        }
    }

    /** Some com o popup. Se [id] for dado, só some se o popup for desse pedido. */
    fun esconder(id: String? = null) {
        if (id != null && id != pedidoId) return
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        pedidoId = null
    }
}
