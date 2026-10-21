FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY . .

RUN chmod +x ./mvnw && ./mvnw -B verify

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/sis-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
