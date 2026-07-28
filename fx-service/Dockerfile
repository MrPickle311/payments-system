FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/fx-service-*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
