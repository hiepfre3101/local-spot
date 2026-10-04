# Build: tải dependency trước khi chép source → đổi code không phải tải lại dependency (cache layer)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Test / Spotless / SpotBugs chạy ở CI (phase verify), image chỉ đóng gói
RUN ./mvnw -B -q -DskipTests package && cp target/backend-*.jar app.jar

# Runtime: chỉ JRE, chạy bằng user không phải root
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 localspot
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER localspot
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
