#  Guide de Connexion Frontend <-> Backend

Pour que vos microservices communiquent avec le frontend React à la fin du développement, suivez ces 3 étapes.

## 1. Côté Backend (L'Aiguilleur)
L'**API Gateway** (Binôme 1) doit autoriser le frontend. Dans son `application.yml` :
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "http://localhost:5173"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true
```

## 2. Côté Frontend (Le Client API)
Actuellement, le front utilise Supabase. Pour passer au Backend Java, créez un fichier `src/lib/api.ts` :
```ts
import axios from 'axios';

export const api = axios.create({
  baseURL: 'http://localhost:8080/api', // L'URL de votre Gateway
});

// Ajoute automatiquement le token JWT à chaque requête
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

## 3. Remplacement des appels
Dans vos composants, remplacez les appels Supabase par `api`.

**Exemple pour les Gigs :**
*Avant (Supabase) :*
```ts
const { data } = await supabase.from('gigs').select('*');
```

*Après (Microservices) :*
```ts
const { data } = await api.get('/gigs'); // La Gateway redirige vers catalog-service
```

## ⚠️ Point de vigilance : Les IDs
Assurez-vous que vos microservices renvoient les mêmes noms de champs que Supabase (ex: `id`, `title`, `price`) pour éviter de devoir modifier tout le code CSS/HTML du frontend.
