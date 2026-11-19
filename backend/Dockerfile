# The backend image of the local deployment (see Deployment in docs/backend/backend-design.md).
# Built by deploy/compose.yaml; the tests are not run here, they run in continuous integration.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# The dependencies first, so a change of the code does not download them again.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
# The application does not run as root. The item images live in a volume at /data/images.
RUN useradd --system --no-create-home opsflow && mkdir -p /data/images && chown opsflow /data/images
COPY --from=build /src/target/opsflow-backend-*.jar /app/opsflow.jar
USER opsflow
# 8080: the API, which nginx passes on. 8081: Actuator, which nobody outside the containers reaches.
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/opsflow.jar"]
