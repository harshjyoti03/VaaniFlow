# VaaniFlow


A multilingual AI-powered meeting and conversation assistant for Android.

VaaniFlow aims to capture speech, transcribe conversations in near real time, translate supported languages, organize speakers, and export searchable meeting records.

Project Goals
- Reliable Android audio recording
- Offline-first storage and recovery
- Multilingual speech-to-text
- Optional translation and AI-generated summaries
- Speaker labeling and manual correction
- Firebase-powered cloud synchronization
- Interactive HTML transcript and Google Drive export

Technology Stack Android
- Kotlin
- Jetpack Compose
- Room
- Silero VAD

Backend
- Python
- FastAPI
- Pluggable speech and language model providers

Cloud
- Firebase Authentication
- Cloud Firestore
- Google Drive API

Development Principles
- Zero mandatory spending during initial development
- Offline-first recording
- Secure handling of credentials and user data
- Modular, replaceable AI providers
- Tested changes committed incrementally

Free API quotas and hosting availability may limit usage.

Repository Structure
- android/ — Android application
- backend/ — Python API and AI integration
- docs/ — Architecture and development documentation
- scripts/ — Development utilities


Development Status
Phase 0 — Project foundation in progress.