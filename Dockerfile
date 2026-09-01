# Stage 1: Build
FROM eclipse-temurin:21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn -B package -DskipTests

# Stage 2: Custom JRE
FROM eclipse-temurin:21-alpine AS jre-build
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.sql,java.naming,java.desktop,java.management,java.instrument,java.security.jgss,java.xml,jdk.unsupported,jdk.crypto.ec \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /javaruntime

# Stage 3: Runtime
FROM alpine:3.21
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME
WORKDIR /opt/app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]