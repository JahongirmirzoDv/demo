package util

import java.io.File
import java.util.Properties

/**
 * Utility class for loading configuration properties from a file.
 * This helps to keep sensitive information like API keys out of the source code.
 */
object ConfigLoader {
    private val properties = Properties()
    private const val CONFIG_FILE_NAME = "application.properties"
    
    /**
     * Loads the configuration properties from the application.properties file.
     * The file is searched in the following locations:
     * 1. The current working directory
     * 2. The user's home directory
     * 3. The classpath
     */
    fun loadConfig() {
        // Try to load from current directory
        val currentDirFile = File(CONFIG_FILE_NAME)
        if (currentDirFile.exists()) {
            currentDirFile.inputStream().use { properties.load(it) }
            return
        }
        
        // Try to load from home directory
        val homeDirFile = File(System.getProperty("user.home"), CONFIG_FILE_NAME)
        if (homeDirFile.exists()) {
            homeDirFile.inputStream().use { properties.load(it) }
            return
        }
        
        // Try to load from classpath
        val resourceStream = ConfigLoader::class.java.classLoader.getResourceAsStream(CONFIG_FILE_NAME)
        if (resourceStream != null) {
            resourceStream.use { properties.load(it) }
            return
        }
        
        // If no config file is found, log a warning
        println("Warning: No configuration file found. Using default values or environment variables.")
    }
    
    /**
     * Gets a property value from the loaded properties or environment variables.
     * If the property is not found in the properties file, it tries to get it from environment variables.
     * 
     * @param key The property key
     * @param defaultValue The default value to return if the property is not found
     * @return The property value or the default value if not found
     */
    fun getProperty(key: String, defaultValue: String = ""): String {
        // First try to get from properties file
        val fromProperties = properties.getProperty(key)
        if (fromProperties != null) {
            return fromProperties
        }
        
        // Then try to get from environment variables
        val fromEnv = System.getenv(key)
        if (fromEnv != null) {
            return fromEnv
        }
        
        // Finally return the default value
        return defaultValue
    }
}