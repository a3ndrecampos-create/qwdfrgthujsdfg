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
import android.widget.Button
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
                setPadding(dp(20), dp(18), dp(20), dp(18))
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(Color.parseColor("#17233A"))
                    setStroke(dp(2), Color.parseColor("#FFC83D"))
                }
            }
            fun texto(t: String, sp: Float, cor: String, negrito: Boolean = false) = TextView(ctx).apply {
                text = t; textSize = sp; setTextColor(Color.parseColor(cor))
                if (negrito) setTypeface(typeface, Typeface.BOLD)
            }
            caixa.addView(texto("Novo pedido — ${reais(p.totalCentavos)}", 20f, "#FFC83D", true))
            caixa.addView(texto(p.itens.joinToString("\n") { "${it.qtd}× ${it.nome}" }, 17f, "#F4F6FA").apply {
                setPadding(0, dp(8), 0, dp(12))
            })
            val botoes = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            botoes.addView(Button(ctx).apply {
                text = "Confirmar Pix recebido"
                isAllCaps = false
                setOnClickListener { aoConfirmar(p.id) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            botoes.addView(Button(ctx).apply {
                text = "Fechar"
                isAllCaps = false
                setOnClickListener { esconder() }
            })
            caixa.addView(botoes)

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // NOT_FOCUSABLE: o toque vai para o popup, mas o teclado/Uber por trás continuam normais.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = dp(40)
                horizontalMargin = 0.03f
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
