FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
RUN groupadd -r app && useradd -r -g app app
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
