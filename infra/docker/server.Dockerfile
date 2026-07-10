FROM gradle:9.4.1-jdk17 AS build
WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV PORT=8080
COPY --from=build /workspace/server/build/install/server/ ./
EXPOSE 8080
CMD ["./bin/server"]
