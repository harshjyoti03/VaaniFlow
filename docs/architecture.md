# VaaniFlow Architecture

## 1. Android Application
Responsibilities:
- Microphone capture and recording lifecycle
- Voice activity detection and audio chunking
- Local audio persistence using Room
- Live transcript interface
- Session review and export controls
## 2. Backend
Technology: Python + FastAPI

Responsibilities:
- Validate incoming audio chunks
- Authenticate requests
- Route requests to configured AI providers
- Normalize transcription responses
- Handle retries, rate limits, and failures
## 3. Firebase
- Firebase Authentication for user identity
- Cloud Firestore for session metadata and transcript synchronization
- Security rules restricting data access to authorized users
## 4. AI Services
AI providers must be replaceable through backend service interfaces.

Initial priority:
- Speech-to-text
- Language identification and translation
- Optional speaker diarization
- AI summaries and action items
## 5. Storage
- Room: local sessions, audio chunks, and offline queue
- Firestore: synchronized metadata and transcript segments
- Google Drive: exported audio, HTML transcript, and summary
## 6. Reliability
- Persist audio before uploading
- Use stable session and chunk identifiers
- Make retries idempotent
- Preserve data during network outages
- Never assume free hosting remains continuously available
## 7. Privacy and Security
- Never commit API keys or OAuth tokens
- Validate authentication on protected backend endpoints
- Enforce user-scoped Firestore security rules
- Obtain appropriate consent before recording conversations
- Provide controls for deleting recordings and stored profiles