FROM node:16 as builder

WORKDIR /opt/app
RUN mkdir /opt/build

COPY package.json yarn.lock ./
RUN yarn

COPY . .