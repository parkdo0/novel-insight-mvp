# [Stage 1] 빌드 단계 (Node.js 환경)
FROM node:20-alpine as build-stage
WORKDIR /app
# 패키지 정보 복사 및 설치
COPY package*.json ./
RUN npm install

# 소스 복사 및 빌드 (TypeScript 컴파일 포함)
COPY . .
RUN npm run build

# [Stage 2] 실행 단계 (Nginx 환경)
FROM nginx:stable-alpine as production-stage

# 빌드 결과물(dist 폴더)만 쏙 빼와서 Nginx에 넣기
COPY --from=build-stage /app/dist /usr/share/nginx/html

# 작성한 Nginx 설정 파일 덮어쓰기
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]