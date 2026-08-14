FROM node:20-alpine
WORKDIR /app
COPY package.json ./
COPY server.js ./
COPY railway.json ./
RUN npm install --omit=dev
ENV NODE_ENV=production
CMD ["npm", "start"]
