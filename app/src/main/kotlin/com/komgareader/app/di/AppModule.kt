package com.komgareader.app.di

import android.content.Context
import android.os.Build
import coil.ImageLoader
import com.komgareader.plugin.host.PluginHost
import com.komgareader.app.data.coil.SourceCoverFetcher
import com.komgareader.app.data.coil.SourcePageFetcher
import com.komgareader.app.di.ApplicationScope
import com.komgareader.app.eink.HardwareButtonBus
import com.komgareader.app.eink.NoOpEinkController
import com.komgareader.domain.eink.EinkController
import com.komgareader.domain.render.DocumentFactory
import com.komgareader.domain.render.ReflowableDocumentFactory
import com.komgareader.domain.usecase.NovelProgressMapper
import com.komgareader.eink.onyx.OnyxEinkController
import com.komgareader.domain.source.SourceManager
import com.komgareader.render.crengine.CrengineDocumentFactory
import com.komgareader.render.mupdf.MupdfDocumentFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Die eine prozessweite Quellen-Registry (Naht A). Als Singleton bereitgestellt, damit
     * [com.komgareader.app.data.SourceRegistration] und der [SourcePageFetcher] dieselbe
     * Instanz teilen — der Fetcher sieht genau die Quellen, die registriert wurden.
     */
    @Provides
    @Singleton
    fun sourceManager(): SourceManager = SourceManager()

    /**
     * App-weiter Coil-[ImageLoader] mit registriertem [SourcePageFetcher.Factory]: Bilder
     * werden über die Quellen-Naht ([SourceManager] → [com.komgareader.domain.source.BrowsableSource.openPage])
     * geladen, statt quellenspezifische URLs + Auth-Header durch die UI zu reichen.
     */
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext ctx: Context,
        sources: SourceManager,
    ): ImageLoader =
        ImageLoader.Builder(ctx)
            .components {
                add(SourcePageFetcher.Factory(sources))
                add(SourceCoverFetcher.Factory(sources))
            }
            .build()

    @Provides
    @Singleton
    fun novelProgressMapper(): NovelProgressMapper = NovelProgressMapper()

    /**
     * Render-Factory (Naht B) hinter dem [DocumentFactory]-Interface (DIP): MuPDF rendert
     * cbz/cbr/pdf für lokale Downloads. Der Reader kennt nur das Interface — eine andere
     * Engine klinkt sich hier ein, ohne den Reader zu berühren.
     */
    @Provides
    @Singleton
    fun documentFactory(): DocumentFactory = MupdfDocumentFactory()

    /**
     * Reflow-Engine-Factory (Naht B) hinter dem [ReflowableDocumentFactory]-Interface.
     * Der Reader kennt nur das Interface, nie die konkrete crengine-Implementierung —
     * die Naht bleibt geschlossen. Singleton, damit der prozessweite Font-Manager-
     * Bootstrap genau einmal über die App-Lebenszeit läuft.
     */
    @Provides
    @Singleton
    fun reflowableDocumentFactory(@ApplicationContext ctx: Context): ReflowableDocumentFactory =
        CrengineDocumentFactory(ctx)

    /**
     * App-weit lebender [CoroutineScope] für Arbeit, die den Lifecycle einer einzelnen
     * Komponente (z. B. eines ViewModels) überdauern muss — etwa das Persistieren des
     * Lesefortschritts beim Schließen des Readers (offline-first, darf nicht verloren
     * gehen). [SupervisorJob], damit ein Fehler in einem Job die anderen nicht abbricht.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Plugin-Lader (Naht A, Phase 4): entdeckt und lädt Quellen-Plugin-APKs via PackageManager.
     * Sicherheits-Gate: TOFU-Signatur-Pin in [SourceRegistration] — Plugins werden nur ausgeführt,
     * wenn die aktuelle APK-Signatur mit dem beim Hinzufügen gepinnten Cert-SHA-256 übereinstimmt.
     */
    @Provides
    @Singleton
    fun providePluginHost(@ApplicationContext context: Context): PluginHost = PluginHost(context)

    @Provides
    @Singleton
    fun einkController(
        bus: HardwareButtonBus,
        @ApplicationContext ctx: Context,
    ): EinkController {
        return if (Build.MANUFACTURER.equals("ONYX", ignoreCase = true)) {
            OnyxEinkController(
                buttonEvents_ = bus.events,
                appPackageName = ctx.packageName,
            )
        } else {
            NoOpEinkController(bus)
        }
    }
}
