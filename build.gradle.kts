plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.economicbriefing"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.pgvector:pgvector:0.1.6")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // RSS Parsing
    implementation("com.rometools:rome:2.1.0")

    // Jackson
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Retry
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework:spring-aspects")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// 운영은 NSSM 서비스(scripts/install-service.ps1)가 bootJar 산출물을 java -jar로 띄웁니다.
// bootRun은 사용하지 않으므로 비활성화합니다. 환경변수는 서비스 등록 시 .env에서 주입됩니다.
// enabled=false로 두면 조용히 SKIPPED 되어 "성공했는데 서버가 없다"로 오해합니다. 명시적으로 실패시킵니다.
tasks.bootRun {
    doFirst {
        throw GradleException("bootRun은 사용하지 않습니다. .\\gradlew.bat clean bootJar 후 scripts\\install-service.ps1 로 서비스를 운영하세요.")
    }
}
