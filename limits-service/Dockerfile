FROM eclipse-temurin:25-jre-alpine AS byteman-downloader
RUN apk add --no-cache curl unzip && \
    curl -LO https://downloads.jboss.org/byteman/4.0.26/byteman-download-4.0.26-bin.zip && \
    unzip byteman-download-4.0.26-bin.zip && \
    mv byteman-download-4.0.26 /opt/byteman

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache bash
WORKDIR /app
COPY --from=byteman-downloader /opt/byteman /opt/byteman
ENV BYTEMAN_HOME=/opt/byteman
COPY target/limits-service-*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/byteman/lib/byteman.jar=listener:true,port:9288,address:0.0.0.0"
EXPOSE 9091 9288
ENTRYPOINT ["java", "-jar", "app.jar"]
