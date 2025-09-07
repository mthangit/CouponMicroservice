
FROM eclipse-temurin:21.0.7_6-jdk-noble
WORKDIR /app
COPY order-service/target/order-service-*.jar app.jar
EXPOSE 8080
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]

