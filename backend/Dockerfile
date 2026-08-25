



# =====================================================
# Stage 1: Build the Spring Boot app with Maven
# =====================================================
FROM maven:3.9.9-eclipse-temurin-21 AS build
LABEL authors="Yogesh"
WORKDIR /app

# Copy pom.xml first so dependency layer gets cached
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy the rest of the source and build
COPY src ./src
RUN mvn -B clean package -DskipTests

# =====================================================
# Stage 2: Lightweight runtime image
# =====================================================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

COPY --from=build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh

# Create media upload directory and grant write access to the 'spring' user
RUN mkdir -p /app/uploads && chown -R spring:spring /app && chmod -R u+rwX /app \
    && chown spring:spring app.jar \
    && chmod +x /docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/docker-entrypoint.sh"]
