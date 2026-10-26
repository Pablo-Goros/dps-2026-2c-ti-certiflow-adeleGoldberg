# Build stage: Maven also builds the React frontend through frontend-maven-plugin,
# so the resulting jar already serves the UI and no Node stage is needed here.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src

# Dependencies first, so editing sources does not re-download them on every build.
COPY pom.xml ./
COPY core/pom.xml core/
COPY infrastructure/pom.xml infrastructure/
COPY app/pom.xml app/
COPY app/frontend/package.json app/frontend/package-lock.json app/frontend/
RUN mvn -B -q dependency:go-offline -DskipTests

COPY core/src core/src
COPY infrastructure/src infrastructure/src
COPY app/src app/src
COPY app/frontend app/frontend
RUN mvn -B -q clean package -DskipTests \
 && mv app/target/*-exec.jar /src/certiflow.jar

FROM eclipse-temurin:25-jre
WORKDIR /opt/certiflow

# The H2 file database lives here. Without a volume the container starts empty and
# the demo profile reseeds it, which is what makes a demonstration reproducible.
RUN useradd --system --create-home --home-dir /opt/certiflow certiflow \
 && mkdir -p /opt/certiflow/data \
 && chown -R certiflow:certiflow /opt/certiflow
USER certiflow

COPY --from=build --chown=certiflow:certiflow /src/certiflow.jar ./certiflow.jar

EXPOSE 8080
ENV SPRING_DATASOURCE_URL=jdbc:h2:file:/opt/certiflow/data/certiflow
ENTRYPOINT ["java", "-jar", "/opt/certiflow/certiflow.jar"]
