# Cloud File Storage

A multi-user cloud file storage service inspired by Google Drive. Users can register, log in, and manage their files and folders in a secure, cloud-based environment.
This project was inspired by the [Java Backend Learning Roadmap](https://zhukovsd.github.io/java-backend-learning-course/) created by Sergey Zhukov.

You can read the devlog here: [Project DevLog on Notion](https://heavenly-maize-a3d.notion.site/DevLog-191a9dc9d091804c9972c94e70870962?source=copy_link)


## Features

- **User Management:** Registration, login, logout, view current user.
- **File & Folder Operations:** Upload, download, create, delete, rename, move files and folders.
- **Search:** Find files and folders by name.
- **REST API:** Well-documented endpoints for all operations.
- **Frontend:** Single-page React application.
- **Session Management:** Secure sessions using Redis.
- **File Storage:** S3-compatible storage via MinIO.
- **Database:** SQL database for user data (MySQL).
- **Testing:** Integration tests with JUnit, Testcontainers.
- **Dockerized:** Easy local and production setup with Docker Compose.
- **Swagger:** Interactive API documentation.

## Tech Stack

- **Backend:** Java, Spring Boot, Spring Security, Spring Sessions, Spring Data JPA, Maven
- **Frontend:** React, Bootstrap, HTML/CSS
- **Database:**   MySQL
- **File Storage:** MinIO (S3-compatible)
- **Session Store:** Redis
- **Testing:** JUnit, Testcontainers
- **Deployment:** Docker, Docker Compose
- **API Documentation:** Swagger

## Motivation

- Practice advanced Spring Boot features.
- Hands-on experience with Docker and Docker Compose.
- Introduction to NoSQL (S3 for files, Redis for sessions).
- REST integration with a modern React frontend.

## API Overview

All endpoints are under `/api`. Requests and responses use JSON (except file upload/download).

### Authentication

- **Register:** `POST /api/auth/sign-up`
- **Login:** `POST /api/auth/sign-in`
- **Logout:** `POST /api/auth/sign-out`
- **Current User:** `GET /api/user/me`

### File & Folder Management

- **Get Resource Info:** `GET /api/resource?path=...`
- **Delete Resource:** `DELETE /api/resource?path=...`
- **Download Resource:** `GET /api/resource/download?path=...`
- **Move/Rename:** `GET /api/resource/move?from=...&to=...`
- **Search:** `GET /api/resource/search?query=...`
- **Upload:** `POST /api/resource?path=...` (multipart/form-data)
- **List Directory:** `GET /api/directory?path=...`
- **Create Directory:** `POST /api/directory?path=...`

### Error Handling

All error responses include a JSON body with an `error` field containing the error message.

### Swagger

Interactive API docs available at `/swagger-ui.html`.

## File Storage Structure

- All user files are stored in a single S3 bucket (`user-files`).
- Each user has a root folder: `user-{id}-files/`.
- Example: `user-1-files/docs/test.txt` for user with ID 1.

## Frontend

- Single-page React app ([source](https://github.com/zhukovsd/cloud-storage-frontend/)).
- Easily integrated into the Spring Boot backend or served separately via Nginx in Docker.

## Testing

- Integration tests for user and file services.
- Uses Testcontainers for real database and MinIO testing.

## How to Run the Project Locally

1. **Clone the repository**
`git clone https://github.com/RustamLee/clowd-box.git`
   - Navigate to the project directory: `cd clowd-box`
2. Requires Docker and Docker Compose.
   - Ensure they are installed on your machine.
3. Run `docker-compose up` to start all services.
4. Access all services at the following URLs:
   - Frontend at `http://localhost:8080`.
   - MinIO UI at  http://localhost:9001 (minioadmin / minioadminpass)
   - Swagger UI at `http://localhost:8080/swagger-ui/index.html`

## Deployment
The project is planned to be deployed on DigitalOcean 

