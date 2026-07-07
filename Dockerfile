FROM amazoncorretto:21-alpine
WORKDIR /app

# Dockerfile과 같은 폴더(server/)에 있는 jar 파일을 복사
COPY *.jar app.jar

# 파일 업로드 폴더 생성 및 볼륨 지정
RUN mkdir -p uploads
VOLUME /app/uploads

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
