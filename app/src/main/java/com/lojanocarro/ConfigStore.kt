package com.lojanocarro

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("loja_no_carro")
private val CHAVE_CONFIG = stringPreferencesKey("config_json")
private val CHAVE_VENDAS = stringPreferencesKey("vendas_json")

/** Guarda a configuração do motorista (chave Pix, produtos etc.) no próprio aparelho. */
class ConfigStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val configFlow: Flow<LojaConfig?> = context.dataStore.data.map { prefs ->
        prefs[CHAVE_CONFIG]?.let { runCatching { json.decodeFromString<LojaConfig>(it) }.getOrNull() }
    }

    /** Histórico de vendas pagas (faturamento). Fica só no celular do motorista. */
    suspend fun lerVendas(): List<Venda> =
        context.dataStore.data.first()[CHAVE_VENDAS]
            ?.let { runCatching { json.decodeFromString<List<Venda>>(it) }.getOrNull() }
            ?: emptyList()

    suspend fun salvarVendas(vendas: List<Venda>) {
        context.dataStore.edit { it[CHAVE_VENDAS] = json.encodeToString(vendas) }
    }

    suspend fun salvar(config: LojaConfig) {
        context.dataStore.edit { it[CHAVE_CONFIG] = json.encodeToString(config) }
    }
}
