package com.komgareader.domain.repository

import com.komgareader.domain.model.ColorProfile
import kotlinx.coroutines.flow.Flow

/** Verwaltung der E-Ink-Farbfilter-Profile. Quellen-/geräteneutral. */
interface ColorProfileRepository {
    /** Alle Profile (Built-ins zuerst), reaktiv. */
    fun observeAll(): Flow<List<ColorProfile>>

    /** Das aktive Profil; fällt auf [ColorProfile.OFF] zurück, nie leer. */
    fun observeActive(): Flow<ColorProfile>

    /** Legt an oder aktualisiert; gibt die id zurück. Built-ins dürfen nicht aktualisiert werden. */
    suspend fun upsert(profile: ColorProfile): Long

    /** Löscht ein Custom-Profil. Built-ins werden ignoriert. */
    suspend fun delete(id: Long)

    /** Markiert das Profil [id] als aktiv. */
    suspend fun setActive(id: Long)
}
