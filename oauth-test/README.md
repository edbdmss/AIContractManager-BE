# OAuth test page

Start the Spring Boot application with the `local` profile, then run:

```bash
python3 oauth-test/server.py
```

Open http://localhost:3000 and choose Kakao or Naver. Register the exact callback URL
`http://localhost:3000/oauth/callback` in both provider consoles first. The page calls
the backend at `http://localhost:8080` and keeps the issued access token only in memory.
While the Naver app is in development mode, the account used for testing must be
registered as a tester in the Naver developer console.
Stop the test server with Ctrl+C.
