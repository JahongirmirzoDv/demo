# My Application (Demo)

A demonstration Kotlin Compose for Desktop application showcasing user authentication with Supabase.

## Table of Contents

- [Description](#description)
- [Features](#features)
- [Tech Stack & Dependencies](#tech-stack--dependencies)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [How to Run](#how-to-run)
- [Project Structure](#project-structure)
- [Logging](#logging)

## Description

This application serves as a basic example of how to build a desktop application using Jetpack Compose for Desktop. It demonstrates:
- A simple user interface.
- User authentication (login) using Supabase GoTrue.
- Dependency injection using Koin.
- Configuration management for external services (Supabase).

## Features

-   **User Authentication**: Secure login functionality using email and password, powered by Supabase.
-   **Basic UI**: A simple interface built with Compose for Desktop, including a login screen and a basic post-login view.
-   **Dependency Injection**: Utilizes Koin for managing dependencies like ViewModels and Supabase client.

## Tech Stack & Dependencies

-   **Programming Language**: Kotlin
-   **UI Framework**: Jetpack Compose for Desktop
-   **Backend as a Service (BaaS)**: Supabase (for authentication)
    -   `supabase-kt` (GoTrue client)
-   **Dependency Injection**: Koin
-   **Build Tool**: Gradle
-   **Logging**: Log4j 2 via SLF4J

## Prerequisites

-   Java Development Kit (JDK) 11 or higher.
-   A Supabase project set up with authentication enabled. You will need your Supabase project URL and anon key.

## Configuration

To connect the application to your Supabase project, you need to configure your Supabase URL and Key.

1.  Create a file named `application.properties` in the `src/main/resources/` directory if it doesn't already exist.
2.  Add your Supabase credentials to this file:

    ```properties
    SUPABASE_URL=your_supabase_project_url
    SUPABASE_KEY=your_supabase_anon_key
    ```

    Replace `your_supabase_project_url` and `your_supabase_anon_key` with your actual Supabase project URL and public anon key.

## How to Run

1.  **Clone the repository** (if applicable).
2.  **Ensure Configuration**: Make sure you have set up the `application.properties` file as described in the [Configuration](#configuration) section.
3.  **Build the project**: Open the project in IntelliJ IDEA or use the Gradle wrapper in the terminal.
    ```bash
    ./gradlew build
    ```
4.  **Run the application**:
    -   You can run the `main` function in `src/main/kotlin/Main.kt` directly from IntelliJ IDEA.
    -   Alternatively, use the Gradle task:
        ```bash
        ./gradlew run
        ```

## Project Structure

A brief overview of important directories and files:

-   `src/main/kotlin/Main.kt`: Entry point of the application.
-   `src/main/kotlin/ui/App.kt`: Main Composable function defining the app structure and navigation.
-   `src/main/kotlin/LoginScreen.kt`: Composable for the login UI.
-   `src/main/kotlin/LoginViewModel.kt`: ViewModel handling login logic.
-   `src/main/kotlin/viewmodel/MainViewModel.kt`: ViewModel for the main application state after login.
-   `src/main/kotlin/di/`: Contains Koin modules for dependency injection (`initKoin.kt`, `supabaseModule.kt`, `viewModelModule.kt`).
-   `src/main/kotlin/util/ConfigLoader.kt`: Utility for loading configurations (e.g., Supabase credentials).
-   `src/main/resources/application.properties`: Configuration file for Supabase URL and key (you need to create/update this).
-   `src/main/resources/log4j2.xml`: Configuration for Log4j2 logging.
-   `build.gradle.kts`: Gradle build script defining project dependencies and plugins.
-   `logs/`: Directory where application logs are stored (e.g., `app.log`).

## Logging

The application uses Log4j 2 for logging.
-   Log configurations can be found in `src/main/resources/log4j2.xml`.
-   By default, logs are written to the `logs/app.log` file in the project's root directory.

---

Feel free to modify this README to better suit your project's specifics!
