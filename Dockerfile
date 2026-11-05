# Etapa de construcción
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY --chmod=0755 mvnw ./mvnw
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties \
    && printf '\n' >> .mvn/wrapper/maven-wrapper.properties \
    && chmod +x mvnw \
    && ./mvnw -B package -DskipTests

# Etapa de ejecución
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S restopilot && adduser -S restopilot -G restopilot
COPY --from=build --chown=restopilot:restopilot /app/target/*.jar app.jar
USER restopilot
ENV PORT=8081
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]