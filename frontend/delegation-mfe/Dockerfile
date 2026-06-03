FROM nginx:alpine

# Copy dist files
COPY dist/ /usr/share/nginx/html/

# Custom nginx config with CORS + caching
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
