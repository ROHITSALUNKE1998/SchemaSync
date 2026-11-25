FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
COPY fixtures fixtures
RUN mvn -B package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/schemasync-0.1.0.jar audit.jar
ENTRYPOINT ["java","-jar","audit.jar"]
CMD ["/demo/catalog.jsonl","/demo/mapping.json"]
