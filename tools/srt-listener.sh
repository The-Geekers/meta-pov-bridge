#!/usr/bin/env bash
set -euo pipefail

PORT="${1:-9000}"
IMAGE="${FFMPEG_IMAGE:-ghcr.io/jrottenberg/ffmpeg:9.0.1-ubuntu2404}"
URL="srt://0.0.0.0:${PORT}?mode=listener&transtype=live&latency=500000"

echo "Meta POV Bridge - SRT diagnostic listener"
echo "Port: ${PORT}/udp"
echo "Image: ${IMAGE}"

docker run --rm --network host "${IMAGE}" \
  -hide_banner -loglevel info \
  -i "${URL}" \
  -map 0:v:0 -c copy -f null -
