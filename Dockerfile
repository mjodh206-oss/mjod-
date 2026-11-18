FROM node:22-bookworm-slim
WORKDIR /srv/interpreter
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
COPY --chown=node:node server ./server
COPY --chown=node:node app/src/main/assets ./app/src/main/assets
USER node
EXPOSE 3000
CMD ["node","server/index.mjs"]
