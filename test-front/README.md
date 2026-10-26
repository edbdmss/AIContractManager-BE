# Test frontend

Run the Spring Boot backend first, then start this static test frontend:

```bash
python3 test-front/server.py
```

Open `http://localhost:3000`. The page supports OAuth login, contract creation, contract listing, monthly amount verification, soft deletion, document upload, download, and soft deletion.

Open `http://localhost:3000/wireframe.html` to view the screen wireframe based on the whiteboard flow. The wireframe uses the backend API for OAuth login/signup, contract CRUD, and document upload/list/download/delete.

Register `http://localhost:3000/wireframe.html` as the Kakao and Naver OAuth callback URL. Payment suggestions, calendar data, and chatbot responses remain mock data until their backend APIs are implemented. The backend must be available at `http://localhost:8080` and the local OAuth keys must be configured.
