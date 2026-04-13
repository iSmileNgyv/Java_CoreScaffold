FROM gradle:8.11.1-jdk21-alpine AS builder
WORKDIR /workspace
COPY gradlew build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM alpine:3.21
WORKDIR /opt/argus
COPY --from=builder /workspace/build/libs/ArgusOmni-CLI-*.jar /opt/argus/ArgusOmni-CLI.jar
VOLUME ["/export"]
CMD ["sh", "-c", "cp /opt/argus/ArgusOmni-CLI.jar /export/ArgusOmni-CLI.jar && echo 'ArgusOmni JAR exported to /export/ArgusOmni-CLI.jar'"]
