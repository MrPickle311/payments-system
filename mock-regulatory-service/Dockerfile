FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/mock-regulatory-service-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
