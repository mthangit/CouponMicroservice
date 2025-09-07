# Sử dụng JDK để chạy ứng dụng
FROM eclipse-temurin:21.0.7_6-jdk-noble
WORKDIR /app
COPY api-gateway/target/api-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]