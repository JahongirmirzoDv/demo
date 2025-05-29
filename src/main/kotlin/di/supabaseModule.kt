// src/main/kotlin/di/SupabaseModule.kt

package di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.koin.dsl.module
import util.ConfigLoader
import viewmodel.LoginViewModel

private val logger: Logger = LogManager.getLogger("SupabaseModule")

val supabaseModule = module {
    factory { LoginViewModel(supabaseClient = get()) }

    single<SupabaseClient> {
        val supabaseUrl = ConfigLoader.getProperty("SUPABASE_URL", "https://xovedzejjcuoqzrqbyzf.supabase.co")
        val supabaseKey = ConfigLoader.getProperty("SUPABASE_KEY", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhvdmVkemVqamN1b3F6cnFieXpmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDgwMTIxMjAsImV4cCI6MjA2MzU4ODEyMH0.bAvwpKnLijAS2kvrXOwM4QBqulsRUbdE91KEWnwq2b0")

        logger.info("Initializing Supabase client with URL: $supabaseUrl")
        if (supabaseKey == "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhvdmVkemVqamN1b3F6cnFieXpmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDgwMTIxMjAsImV4cCI6MjA2MzU4ODEyMH0.bAvwpKnLijAS2kvrXOwM4QBqulsRUbdE91KEWnwq2b0") {
            logger.warn("Using default Supabase key. For production, set SUPABASE_KEY environment variable or in application.properties")
        }

        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Auth) {
                scheme = "io.jan.supabase"
                host = "login"
            }
        }
    }
}
