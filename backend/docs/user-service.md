# User Service

## Responsabilité

Gestion des profils utilisateurs.

## Fonctionnalités

- Profil étudiant
- Profil client
- Upload CNI
- Upload carte étudiant
- Portfolio
- Compétences
- Génération CV PDF

## Entités

### StudentProfile

- id
- fullname
- university
- department
- skills
- verified

### ClientProfile

- id
- fullname
- city
- companyType

## Endpoints

GET /api/users/{id}

PUT /api/users/{id}

POST /api/users/upload-documents

POST /api/users/portfolio

## Intégrations

- Cloudinary
- PDF generation

## Workflow vérification

1. Étudiant upload documents
2. Admin reçoit demande
3. Validation manuelle
4. Badge vérifié