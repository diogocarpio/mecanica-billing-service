FROM node:24-alpine

ENV NODE_ENV=production
WORKDIR /app
COPY server.js ./server.js
USER node
EXPOSE 4010
CMD ["node", "server.js"]
