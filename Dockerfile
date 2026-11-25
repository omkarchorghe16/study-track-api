FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY target/study-track-api-*.jar app.jar
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
