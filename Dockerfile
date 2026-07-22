# Image du frontend : build Vite puis service statique par nginx.
#
# Ce meme nginx sert aussi de porte d'entree pour l'API (voir docker/nginx.conf).
# Le front et l'API partagent donc la meme origine, ce qui supprime toute
# question de CORS et n'expose qu'un seul port au reverse proxy du VPS.

FROM node:20-alpine AS build

WORKDIR /app

# Les dependances changent moins souvent que le code : on les installe d'abord
# pour que le cache Docker survive a une simple modification de composant.
COPY package.json package-lock.json ./
# --no-audit / --no-fund evitent deux appels reseau inutiles. Les reglages de
# retry rattrapent les coupures passageres du registre npm, qui font
# autrement echouer tout le build sur un simple ECONNRESET.
RUN npm config set fetch-retries 5 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm ci --no-audit --no-fund

COPY . .

# Chaine vide par defaut : le frontend appelle alors /api/... en chemin relatif,
# donc le nginx de cette meme image, qui relaie vers l'api-gateway.
# api.ts utilise `?? "http://localhost:8080"`, un fallback qui ne se declenche
# que sur undefined : la chaine vide est bien conservee.
ARG VITE_API_URL=""
RUN printf 'VITE_API_URL=%s\n' "$VITE_API_URL" > .env.production \
    && npm run build


FROM nginx:1.27-alpine AS runtime

# Le port 80 par defaut de l'image est inutile ici : la stack est publiee sur
# 127.0.0.1:8090 et les ports 80/443 de la machine appartiennent a une autre
# application.
RUN rm /etc/nginx/conf.d/default.conf

COPY docker/nginx.conf /etc/nginx/conf.d/kametud.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

# 127.0.0.1 et non « localhost » : dans ce conteneur, localhost ne resout que
# vers ::1 alors que nginx ecoute en IPv4. La sonde echouerait sur
# « Connection refused » alors que le service repond normalement.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
