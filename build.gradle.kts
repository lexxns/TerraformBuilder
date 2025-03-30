import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import terraformbuilder.codegen.ResourceTypeGenerator

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "1.8.20"
}

group = "com.lexxns"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

// Configure Kotlin JVM target
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation("com.bertramlabs.plugins:hcl4j:0.9.4")

    // Add SLF4J implementation
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // JUnit 4 dependencies
    testImplementation("junit:junit:4.13.2")

    // Use kotlin test with JUnit 4
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.8.20")

    // Add compose testing dependencies
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs)
}

// Configure tests to use JUnit 4
tasks.test {
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Configure Java compatibility for the project
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "TerraformBuilder"
            packageVersion = "1.0.0"
        }
    }
}

tasks.register<Task>("generateTerraformSchemas") {
    group = "terraform"
    description = "Generates Terraform provider schemas for supported versions"

    doLast {
        // Define AWS provider versions we want to support
        val awsVersions = listOf(
            "5.92.0", // Latest stable version as of now
            "5.91.0"
        )

        awsVersions.forEach { version ->
            val versionDirName = version.replace(".", "_")

            // Ensure resources directory exists
            val resourceDir = file("src/main/resources/terraform.aws/$versionDirName")
            if (!resourceDir.exists()) {
                if (!resourceDir.mkdirs()) {
                    logger.error("Failed to create resource directory: ${resourceDir.absolutePath}")
                    return@forEach
                }
                logger.lifecycle("Created resource directory: ${resourceDir.absolutePath}")
            }

            // Check if schema file already exists
            val schemaFile = file("${resourceDir.absolutePath}/schema.json")
            if (schemaFile.exists()) {
                logger.lifecycle("Schema file already exists for AWS provider version $version, skipping generation")
                return@forEach
            }

            // Create temporary working directory
            val workDir = layout.buildDirectory.dir("terraform-temp/$versionDirName").get().asFile
            if (!workDir.exists() && !workDir.mkdirs()) {
                logger.error("Failed to create working directory: ${workDir.absolutePath}")
                return@forEach
            }
            logger.lifecycle("Created working directory: ${workDir.absolutePath}")

            // Create terraform config file
            val mainTf = File(workDir, "main.tf")
            try {
                mainTf.writeText(
                    """
                    terraform {
                      required_providers {
                        aws = {
                          source  = "hashicorp/aws"
                          version = "$version"
                        }
                      }
                    }
                """.trimIndent()
                )
                logger.lifecycle("Created Terraform config file: ${mainTf.absolutePath}")
            } catch (e: Exception) {
                logger.error("Failed to create Terraform config file: ${e.message}")
                return@forEach
            }

            // Initialize terraform
            logger.lifecycle("Initializing Terraform for AWS provider version $version...")
            val initResult = project.exec {
                workingDir = workDir
                commandLine = listOf("terraform", "init")
                isIgnoreExitValue = true
            }

            if (initResult.exitValue == 0) {
                logger.lifecycle("Terraform initialized successfully for version $version")

                // Generate schema
                logger.lifecycle("Generating schema to: ${schemaFile.absolutePath}")

                try {
                    // Make sure parent directory exists
                    schemaFile.parentFile.mkdirs()

                    val schemaResult = project.exec {
                        workingDir = workDir
                        commandLine = listOf("terraform", "providers", "schema", "-json")
                        standardOutput = schemaFile.outputStream()
                        isIgnoreExitValue = true
                    }

                    if (schemaResult.exitValue != 0) {
                        logger.error("Failed to generate schema for AWS provider version $version")
                        if (schemaFile.exists()) {
                            schemaFile.delete() // Clean up partial file
                        }
                    } else {
                        logger.lifecycle("Successfully generated schema for AWS provider version $version")
                    }
                } catch (e: Exception) {
                    logger.error("Error during schema generation: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                logger.error("Failed to initialize Terraform for AWS provider version $version")
            }
        }
    }
}
tasks.named("processResources") {
    dependsOn("generateTerraformSchemas")
}

tasks.register("generateTerraformTypes") {
    group = "terraform"
    description = "Generates Terraform resource types and schemas"
    dependsOn("generateTerraformSchemas")

    doLast {
        // Get the latest schema file from resources
        val resourcesDir = file("src/main/resources/terraform.aws")
        if (!resourcesDir.exists()) {
            throw IllegalStateException("Resources directory not found at ${resourcesDir.absolutePath}")
        }

        val latestVersion = resourcesDir.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
            ?: throw IllegalStateException("No schema files found in ${resourcesDir.absolutePath}")

        val schemaFile = file("${latestVersion.absolutePath}/schema.json")
        if (!schemaFile.exists()) {
            throw IllegalStateException("Schema file not found at ${schemaFile.absolutePath}")
        }

        // Generate ResourceType enum from the schema
        val generator = ResourceTypeGenerator()
        generator.generateResourceTypeEnum(
            schemaFile = schemaFile,
            outputFile = file("src/main/kotlin/terraformbuilder/ResourceType.kt")
        )
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateTerraformTypes")
}