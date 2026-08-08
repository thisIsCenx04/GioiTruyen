FROM gcr.io/distroless/java21-debian13:nonroot@sha256:c1ab839be0b871268e437a008e154be87f8fabca0202dcd393633c7b263b8e78

WORKDIR /app

COPY --chown=65532:65532 \
    backend/build/libs/story-platform-*.jar \
    /app/application.jar

USER 65532:65532

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]
