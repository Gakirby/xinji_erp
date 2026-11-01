# =====================================================================
#  后端镜像：Maven 构建 → JRE 运行
#  docker build -f server/Dockerfile -t xinji-erp-server ./server
# =====================================================================

FROM maven:3.9-eclipse-temurin-8 AS build

WORKDIR /build

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:8-jre

WORKDIR /app

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -Dfile.encoding=UTF-8"

COPY --from=build /build/target/xinji-erp.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=prod"]
