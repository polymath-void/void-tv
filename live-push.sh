#!/bin/bash
set -e

TV_IP="${TV_IP:-192.168.0.108}"
PORT="${PORT:-4444}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN="${VOID_TOKEN:-}"

if [ -z "$TOKEN" ] && [ -f "$DIR/token" ]; then
  TOKEN="$(cat "$DIR/token")"
fi
if [ -z "$TOKEN" ]; then
  TOKEN="$(adb -s "${TV_IP}:5555" shell cat /sdcard/voidtv/token 2>/dev/null || true)"
fi

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
      -H "X-Void-Token: ${TOKEN}" \
      -d "$CODE"
    echo ""
    ;;
  color)
    HEX="$1"
    echo "Setting accent color to $HEX..."
    curl -s -X POST "http://${TV_IP}:${PORT}/eval" \
      -H "Content-Type: text/plain" \
      -H "X-Void-Token: ${TOKEN}" \
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
  update)
    APK="$1"
    if [ -z "$APK" ]; then
      APK="$DIR/voidtv-release.apk"
    fi
    if [ ! -f "$APK" ]; then
      echo "Building release APK first..."
      "$DIR/build.sh"
    fi
    LOCAL_IP="$(ip -4 addr show to 192.168.0.0/16 2>/dev/null | grep -oP '(?<=inet\s)\d+(\.\d+){3}' | head -n1)"
    if [ -z "$LOCAL_IP" ]; then
      LOCAL_IP="192.168.0.115"
    fi
    HTTP_PORT=8844
    echo "Serving $APK on http://${LOCAL_IP}:${HTTP_PORT}/update.apk..."
    python3 -c "
import http.server, socketserver
class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/update.apk':
            with open('$APK', 'rb') as f:
                data = f.read()
            self.send_response(200)
            self.send_header('Content-Type', 'application/vnd.android.package-archive')
            self.send_header('Content-Length', len(data))
            self.end_headers()
            self.wfile.write(data)
        else:
            self.send_error(404)
s = socketserver.TCPServer(('0.0.0.0', $HTTP_PORT), Handler)
s.handle_request()
" &
    SERVER_PID=$!
    sleep 0.5
    echo "Notifying TV LiveServer on port ${PORT} to install update..."
    curl -s -X POST "http://${TV_IP}:${PORT}/update?url=http://${LOCAL_IP}:${HTTP_PORT}/update.apk"
    echo ""
    wait $SERVER_PID 2>/dev/null || true
    echo "✓ App update delivered to TV!"
    ;;
  *)
    echo "Usage: $0 {layout|state|inputs|input <HDMI1|HDMI2|AV|select>|key <UP|DOWN|LEFT|RIGHT|SELECT|BACK|MENU|HOME|0-9>|tap <label>|open <url>|eval <code>|color <#hex>|launch <pkg>|update [apk]|ping}"
    ;;
esac
