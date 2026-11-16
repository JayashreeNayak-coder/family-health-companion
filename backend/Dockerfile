FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN sh ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /var/lib/family-health/documents \
    && chown app:app /var/lib/family-health/documents
WORKDIR /app
COPY --from=build /app/target/family-health-backend-*.jar app.jar
ENV DOCUMENT_STORAGE_DIR=/var/lib/family-health/documents
# Fits a 512 MB host such as Render's free plan (DECISIONS.md, D-033): the heap takes at most 70% of the
# container's memory, leaving room for the rest of the JVM. Serial GC suits one small CPU. A host can override this.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
