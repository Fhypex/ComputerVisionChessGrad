@echo off
REM Convenience script to install requirements and run the MediaPipe FastAPI server
SET PY=python

echo Installing python requirements (may take a while)...
%PY% -m pip install -r "%~dp0..\python\requirements.txt"

echo Starting MediaPipe server on http://127.0.0.1:8000
%PY% "%~dp0..\python\mediapipe_server.py"
