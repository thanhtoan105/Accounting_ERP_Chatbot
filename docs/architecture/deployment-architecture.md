# Deployment Architecture

## Containerization

- Docker Compose for local development
- Separate Dockerfiles for backend and frontend
- Production target: TBD (AWS ECS, Railway, or self-hosted)

## Environment Configuration

- Backend: `application.yml` with profiles (dev, staging, prod)
- Frontend: Environment variables for API endpoints
- Secrets: Environment variables, never in code

---
