package di

import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.dsl.KoinAppDeclaration
import util.ConfigLoader

/**
 * Initializes Koin dependency injection framework.
 * This function should be called at the start of the application.
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    // Load configuration properties
    ConfigLoader.loadConfig()

    // Initialize Koin if not already initialized
    if (GlobalContext.getOrNull() == null) {
        startKoin {
            config?.invoke(this)
            modules(
                supabaseModule,
                viewModelModule,
                // Add more modules here as needed
            )
        }
    }
}
