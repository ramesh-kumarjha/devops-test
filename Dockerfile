# ---- Global Variables ----

#
# ---- Base Node ----
FROM node:12.2.0 AS base
WORKDIR /app
COPY package.json .

#
# ---- Dependencies ----
FROM base AS dependencies
RUN npm set progress=false && npm config set depth 0
RUN npm install

#
# ---- Build ----
FROM dependencies AS build
COPY . .
RUN npm run build

#
# ---- Test ----
# run tests
FROM build AS test
COPY . .
RUN npm run test
# ---- Release ----
FROM nginx:alpine AS pre-release
COPY --from=build /app/build /aap
CMD ["node", "index.js"]
