plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.jpa")
    id("net.mamoe.mirai-console")
}

group = "xyz.cssxsh.mirai.plugin"
version = "1.8.2"

mirai {
    jvmTarget = JavaVersion.VERSION_11
    configureShadow {
        archiveBaseName.set(rootProject.name)
        exclude("module-info.class")
    }
}

repositories {
    mavenLocal()
    maven(url = "https://maven.aliyun.com/repository/central")
    mavenCentral()
    maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
    gradlePluginPortal()
}

dependencies {
    implementation(jsoup(Versions.jsoup))
    implementation(hibernate("hibernate-core", Versions.hibernate))
    implementation(hibernate("hibernate-c3p0", Versions.hibernate))
    implementation("com.github.gwenn:sqlite-dialect:0.1.2") {
        exclude(group = "org.hibernate")
    }
    implementation(xerial("sqlite-jdbc", Versions.sqlite))
    implementation(project(":client")) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
        exclude(group = "io.ktor", module = "ktor-client-core")
        exclude(group = "io.ktor", module = "ktor-client-okhttp")
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    compileOnly("io.github.gnuf0rce:netdisk-filesync-plugin:1.2.1")
    compileOnly("net.mamoe:mirai-core-jvm:2.9.2")
    compileOnly("mysql:mysql-connector-java:8.0.26")

    testImplementation(kotlin("test", "1.5.31"))
    testImplementation("net.mamoe.yamlkt:yamlkt:0.10.2")
    testImplementation("mysql:mysql-connector-java:8.0.26")
}

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}

tasks {
    compileKotlin {
        kotlinOptions.freeCompilerArgs += "-Xunrestricted-builder-inference"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
    test {
        useJUnitPlatform()
    }
}