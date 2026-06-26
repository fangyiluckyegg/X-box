# 构建阶段：node打包dist
FROM node:18-alpine AS builder
WORKDIR /app
COPY . .
RUN npm install
RUN npm run build

# 运行阶段：纯nginx托管静态页面
FROM nginx:1.25-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
# SPA路由配置
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
