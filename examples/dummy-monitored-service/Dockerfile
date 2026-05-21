FROM eclipse-temurin:21-jdk AS build

WORKDIR /src
COPY src/DummyMonitoredService.java .
RUN javac DummyMonitoredService.java

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /src/*.class /app/

EXPOSE 8080
ENTRYPOINT ["java", "DummyMonitoredService"]
