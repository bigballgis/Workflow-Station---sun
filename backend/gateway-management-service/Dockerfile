FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/gateway-management-service-*.jar app.jar

EXPOSE 8091

ENTRYPOINT ["java", "-jar", "app.jar"]
