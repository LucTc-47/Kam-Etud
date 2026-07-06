# Architecture Microservices — Kam'Etud (Spring Boot)

> Document de référence pour la migration du monolithe Supabase actuel vers une architecture microservices basée sur **Spring Boot 3.x / Java 21**, déployée en conteneurs Docker et orchestrée via Kubernetes ou Docker Compose.

---

## 1. Base de données — 12 tables

| # | Table | Rôle | Microservice propriétaire |
|---|-------|------|---------------------------|
| 1 | `profiles` | Données utilisateurs (étudiant/client/admin/modérateur) | auth-service |
| 2 | `user_roles` | Rôles séparés (anti-escalade) | auth-service |
| 3 | `categories` | Catégories de services | catalog-service |
| 4 | `cities` | Villes camerounaises | catalog-service |
| 5 | `gigs` | Services proposés (3 paliers) | catalog-service |
| 6 | `gig_requests` | Demandes publiées par les clients | request-service |
| 7 | `request_proposals` | Propositions des étudiants | request-service |
| 8 | `orders` | Commandes / contrats | order-service |
| 9 | `chat_messages` | Messagerie temps réel | chat-service |
| 10 | `reviews` | Avis et notations | moderation-service |
| 11 | `disputes` | Litiges modérés | moderation-service |
| 12 | `verification_requests` | Dossiers KYC | kyc-service |

## 2. Buckets de stockage (4)

| Bucket | Public | Usage | Service |
|---|---|---|---|
| `avatars` | ✅ | Photos de profil (JPG/PNG, max 5 Mo) | media-service |
| `portfolio` | ✅ | Portfolios étudiants | media-service |
| `identity-documents` | 🔒 | KYC | kyc-service |
| `deliverables` | 🔒 | Livrables | order-service |

## 3. Microservices (8 métiers + 3 transverses = 11)

| # | Service | Port | Tables | Endpoints clés |
|---|---------|------|--------|----------------|
| 1 | **auth-service** | 8081 | profiles, user_roles | `/api/auth/{register,login,refresh,me}` |
| 2 | **kyc-service** | 8082 | verification_requests | `/api/kyc/{submit,review,documents}` |
| 3 | **catalog-service** | 8083 | gigs, categories, cities | `/api/gigs`, `/api/search?lat=&lng=&radius=` |
| 4 | **request-service** | 8084 | gig_requests, request_proposals | `/api/requests`, `/api/proposals/{accept,reject}` |
| 5 | **order-service** | 8085 | orders + bucket deliverables | `/api/orders/{create,deliver,revise,complete}` |
| 6 | **payment-service** | 8086 | transactions (interne) | `/api/payments/{init,webhook,release}` |
| 7 | **chat-service** | 8087 | chat_messages | WS `/ws/chat/{orderId}` |
| 8 | **moderation-service** | 8088 | disputes, reviews | `/api/disputes`, `/api/reviews/report` |
| 9 | notification-service | 8089 | — | events → email/SMS/push |
| 10 | analytics-service | 8090 | read-replica | `/api/reports/{pdf,xlsx}` |
| 11 | media-service | 8091 | buckets avatars/portfolio | `/api/media/upload` |

### Stack par service
- **Spring Boot 3.3** + **Spring Web / WebFlux**
- **Spring Data JPA** + PostgreSQL (1 DB par service)
- **Spring Security** + JWT (resource server) — clés signées par auth-service
- **Spring Cloud OpenFeign** (REST inter-service) ou **gRPC** (perf)
- **Spring Kafka** ou **RabbitMQ** pour les events asynchrones
- **Spring Cloud Config** pour la configuration centralisée
- **Eureka** ou **Consul** pour la découverte de service
- **Resilience4j** (circuit breaker, retry)
- **Micrometer + Prometheus + Grafana** pour l'observabilité

### Communication
- **API Gateway** : Spring Cloud Gateway (port `8080`) en frontal
- **Bus d'événements** Kafka topics : `order.created`, `order.status.changed`, `kyc.approved`, `payment.released`, `dispute.opened`, `chat.message.sent`
- **Service mesh** Istio (optionnel, observabilité + mTLS)

---

## 4. Configuration d'un microservice (exemple `order-service`)

### 4.1 `pom.xml` — dépendances minimales
```xml
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
  <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-netflix-eureka-client</artifactId></dependency>
  <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>
  <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka</artifactId></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>
  <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-spring-boot3</artifactId></dependency>
</dependencies>
```

### 4.2 `application.yml`
```yaml
server:
  port: 8085
spring:
  application:
    name: order-service
  datasource:
    url: jdbc:postgresql://order-db:5432/orders
    username: ${DB_USER}
    password: ${DB_PASS}
  jpa:
    hibernate.ddl-auto: validate
  kafka:
    bootstrap-servers: kafka:9092
    consumer.group-id: order-service
  security:
    oauth2.resourceserver.jwt.issuer-uri: http://auth-service:8081
eureka:
  client.service-url.defaultZone: http://discovery:8761/eureka/
management:
  endpoints.web.exposure.include: health,info,prometheus
kametud:
  cors.allowed-origins: https://app.kam-etud.com
```

### 4.3 Sécurité JWT
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
    return http
      .csrf(c -> c.disable())
      .authorizeHttpRequests(a -> a
        .requestMatchers("/actuator/**").permitAll()
        .anyRequest().authenticated())
      .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
      .build();
  }
}
```

### 4.4 `Dockerfile`
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### 4.5 `docker-compose.yml` (extrait)
```yaml
services:
  gateway:        { image: kametud/gateway,        ports: ["8080:8080"] }
  discovery:      { image: kametud/discovery,      ports: ["8761:8761"] }
  config:         { image: kametud/config-server,  ports: ["8888:8888"] }
  auth-service:   { image: kametud/auth,           depends_on: [discovery, auth-db, kafka] }
  order-service:  { image: kametud/order,          depends_on: [discovery, order-db, kafka] }
  chat-service:   { image: kametud/chat,           depends_on: [discovery, chat-db, kafka, redis] }
  kafka:          { image: bitnami/kafka:latest,   ports: ["9092:9092"] }
  redis:          { image: redis:7-alpine }
  order-db:       { image: postgres:16, environment: { POSTGRES_DB: orders } }
```

---

## 5. Connexion du frontend React

Le frontend actuel utilise `@/integrations/supabase/client`. Pour passer aux microservices :

### 5.1 Variables d'environnement (`.env`)
```
VITE_API_BASE_URL=https://api.kam-etud.com        # API Gateway
VITE_WS_URL=wss://api.kam-etud.com/ws             # WebSocket chat-service
```

### 5.2 Client HTTP central (`src/lib/api.ts`)
```ts
import axios from "axios";
export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL });
api.interceptors.request.use((cfg) => {
  const token = localStorage.getItem("kametud_jwt");
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});
api.interceptors.response.use(r => r, async (err) => {
  if (err.response?.status === 401) {
    // tente un refresh via auth-service
    const refresh = localStorage.getItem("kametud_refresh");
    if (refresh) {
      const { data } = await axios.post(`${import.meta.env.VITE_API_BASE_URL}/api/auth/refresh`, { refresh });
      localStorage.setItem("kametud_jwt", data.access);
      err.config.headers.Authorization = `Bearer ${data.access}`;
      return axios.request(err.config);
    }
  }
  throw err;
});
```

### 5.3 Migration des hooks
Remplacer chaque appel `supabase.from('orders').select()` par :
```ts
const { data } = await api.get('/api/orders/mine');
```
Les hooks TanStack Query (`useMyOrders`, `useMyMissions`, etc.) gardent la même signature ; seul le fetcher change.

### 5.4 Chat temps réel (WebSocket)
```ts
const ws = new WebSocket(`${import.meta.env.VITE_WS_URL}/chat/${orderId}?token=${jwt}`);
ws.onmessage = (ev) => {
  const msg = JSON.parse(ev.data);
  queryClient.setQueryData(['chat', orderId], (old: any[] = []) => [...old, msg]);
};
```

### 5.5 Upload de fichiers
```ts
const fd = new FormData();
fd.append("file", file);
const { data } = await api.post('/api/media/upload?bucket=avatars', fd, {
  headers: { 'Content-Type': 'multipart/form-data' }
});
// data.url → à stocker dans profiles.avatar_url via auth-service
```

### 5.6 CORS côté Gateway (Spring Cloud Gateway)
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "https://app.kam-etud.com"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true
```

---

## 6. Synthèse

- **12 tables** PostgreSQL réparties en **8 bases** indépendantes (1 par service métier)
- **4 buckets** de stockage gérés par `media-service` / `kyc-service` / `order-service`
- **11 microservices** Spring Boot (8 métiers + 3 transverses)
- **6 events Kafka** principaux pour la communication asynchrone
- **API Gateway unique** (`/api/*` + `/ws/*`) consommé par le frontend React

> **Migration progressive recommandée** : commencer par extraire `payment-service` (besoin de webhooks Mobile Money) puis `chat-service` (WebSocket scalable), tout en gardant Supabase comme source de données initiale via une stratégie strangler-fig.
