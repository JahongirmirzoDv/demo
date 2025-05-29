// src/main/kotlin/di/SupabaseModule.kt

package di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import org.koin.dsl.module

val supabaseModule = module {
    single<SupabaseClient> {
        createSupabaseClient(
            supabaseUrl = "https://xovedzejjcuoqzrqbyzf.supabase.co", // Replace with your Supabase URL
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhvdmVkemVqamN1b3F6cnFieXpmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDgwMTIxMjAsImV4cCI6MjA2MzU4ODEyMH0.bAvwpKnLijAS2kvrXOwM4QBqulsRUbdE91KEWnwq2b0" // Replace with your Supabase Anon Key
        ) {
            install(Auth) {
                scheme = "io.jan.supabase"
                host = "login"
            }
        }
    }
}