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
    curl -s -G --data-urlencode "pkg=$PKG" "http://${TV_IP}:${PORT}/launch"
    echo ""
    ;;
  state)
    curl -s "http://${TV_IP}:${PORT}/state"
    echo ""
    ;;
  key)
    curl -s -G --data-urlencode "k=$*" "http://${TV_IP}:${PORT}/key"
    echo ""
    ;;
  tap)
    curl -s -G --data-urlencode "label=$*" "http://${TV_IP}:${PORT}/tap"
    echo ""
    ;;
  open)
    curl -s -G --data-urlencode "url=$*" "http://${TV_IP}:${PORT}/open"
    echo ""
    ;;
  inputs)
    curl -s "http://${TV_IP}:${PORT}/inputs"
    echo ""
    ;;
  input)
    INPUT="$1"
    echo "Switching to TV input $INPUT..."
    curl -s -G --data-urlencode "id=$INPUT" "http://${TV_IP}:${PORT}/input"
    echo ""
    ;;
  ping)
    curl -s "http://${TV_IP}:${PORT}/ping"
    echo ""
    ;;
  *)
    echo "Usage: $0 {layout|state|inputs|input <HDMI1|HDMI2|AV|select>|key <UP|DOWN|LEFT|RIGHT|SELECT|BACK|MENU|HOME|0-9>|tap <label>|open <url>|eval <code>|color <#hex>|launch <pkg>|ping}"
    ;;
esac
