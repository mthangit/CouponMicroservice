# Dockerfile for rule-service (Java 21, multi-stage)
FROM eclipse-temurin:21.0.7_6-jdk-noble
WORKDIR /app
COPY rule-service/target/rule-service-*.jar app.jar
EXPOSE 9090
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

