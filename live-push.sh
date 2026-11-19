#!/bin/bash
set -e

TV_IP="${TV_IP:-192.168.0.114}"
PORT="${PORT:-4444}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

CMD="$1"
shift || true

case "$CMD" in
  layout)
    echo "Pushing layout.json to TV..."
    adb -s "${TV_IP}:5555" push "$DIR/assets/runtime/layout.json" /sdcard/voidtv/layout.json
    echo "✓ Pushed. FileObserver will trigger instant reload."
    ;;
  eval)
    CODE="$*"
    echo "Evaluating JS on TV: $CODE"
    curl -s -X POST "http://${TV_IP}:${PORT}/eval" \
      -H "Content-Type: text/plain" \
      -d "$CODE"
    echo ""
    ;;
  color)
    HEX="$1"
    echo "Setting accent color to $HEX..."
    curl -s -X POST "http://${TV_IP}:${PORT}/eval" \
      -H "Content-Type: text/plain" \
      -d "document.documentElement.style.setProperty('--accent', '$HEX'); document.documentElement.style.setProperty('--accent-glow', '${HEX}66');"
    echo "✓ Color applied live."
    ;;
  launch)
    PKG="$1"
    echo "Launching package $PKG..."
    curl -s -X POST "http://${TV_IP}:${PORT}/eval" \
      -H "Content-Type: text/plain" \
      -d "window.Android && window.Android.launchApp('$PKG');"
    echo "✓ Launch command dispatched."
    ;;
  ping)
    curl -s "http://${TV_IP}:${PORT}/ping"
    echo ""
    ;;
  *)
    echo "Usage: $0 {layout|eval <code>|color <#hex>|launch <pkg>|ping}"
    ;;
esac
