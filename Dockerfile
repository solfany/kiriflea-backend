FROM amazoncorretto:21-alpine
WORKDIR /app

# Dockerfile과 같은 폴더(server/)에 있는 jar 파일을 복사
COPY *.jar app.jar

# 파일 업로드 폴더 생성 및 볼륨 지정
RUN mkdir -p uploads

# 컨테이너를 root로 띄우지 않도록 전용 사용자 생성
RUN addgroup -S app && adduser -S app -G app && chown -R app:app /app
USER app

VOLUME /app/uploads

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
