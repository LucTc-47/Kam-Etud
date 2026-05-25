# Architecture Backend Kam'Etud

## Stack Technique

- Spring Boot 3
- Spring Cloud Gateway
- Spring Security + JWT
- Spring Data JPA
- MySQL
- Redis
- WebSocket
- Docker
- Eureka Discovery
- OpenFeign
- RabbitMQ (optionnel)
- Swagger OpenAPI

## Architecture Microservices

Le backend est composé de plusieurs microservices indépendants :

- auth-service
- user-service
- gig-service
- matching-service
- order-service
- payment-service
- messaging-service
- notification-service
- review-service
- admin-service

## Communication

- REST API
- Feign Client
- WebSocket
- Events RabbitMQ

## Sécurité

- JWT Authentication
- OTP SMS
- Role Based Access Control

## Déploiement

- Docker Compose
- Railway / Render