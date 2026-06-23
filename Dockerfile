FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/sateno_b-0.0.1-SNAPSHOT.jar app.jar

# Graceful shutdown support
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
