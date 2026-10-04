FROM node:20-alpine

# Install FFmpeg and required media libraries
RUN apk add --no-cache ffmpeg python3 make g++

WORKDIR /usr/src/app

COPY package*.json ./
COPY prisma ./prisma/

RUN npm install

COPY . .

RUN npm run build

EXPOSE 4000

CMD ["npm", "start"]
