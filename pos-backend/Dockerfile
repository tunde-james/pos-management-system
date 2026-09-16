FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine AS runtime

LABEL description="POS Backend"

RUN apk add --no-cache curl

RUN addgroup -S posuser && adduser -S -G posuser posuser

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

USER posuser

EXPOSE 4000

ENTRYPOINT ["java", "-jar", "app.jar"]

