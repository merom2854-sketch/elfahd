FROM node:20-alpine
WORKDIR /app
COPY android/backend/package.json ./package.json
COPY android/backend/server.js ./server.js
RUN npm install --omit=dev
ENV NODE_ENV=production
CMD ["npm", "start"]
