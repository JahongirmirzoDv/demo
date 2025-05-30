package di

import org.koin.dsl.module
import viewmodel.MainViewModel

/**
 * Koin module for ViewModels
 */
val viewModelModule = module {
    // MainViewModel for document processing
    factory { MainViewModel() }
}
