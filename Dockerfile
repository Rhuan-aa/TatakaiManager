# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Cache de dependências: copia wrapper + pom primeiro
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Código e empacotamento (testes rodam no CI, não na imagem)
COPY src ./src
# application.properties não é versionado; num checkout limpo (ex.: deploy) usa o template
RUN [ -f src/main/resources/application.properties ] || cp src/main/resources/application.properties.example src/main/resources/application.properties
RUN ./mvnw -B -q clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Usuário não-root
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
