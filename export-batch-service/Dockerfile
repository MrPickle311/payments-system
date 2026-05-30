FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/export-batch-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
