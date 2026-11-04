#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/src/main/assets
curl -fL 'https://storage.googleapis/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task' -o app/src/main/assets/hand_landmarker.task
[[ $(stat -c%s app/src/main/assets/hand_landmarker.task) -gt 1000000 ]]
