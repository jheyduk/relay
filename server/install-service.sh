#!/bin/bash
# Install relay-server as a macOS launchd service (LaunchAgent).
# Usage: ./install-service.sh [install|uninstall|restart|status|logs]

PLIST_NAME="dev.heyduk.relay-server"
PLIST_SRC="$(dirname "$0")/${PLIST_NAME}.plist"
PLIST_DST="$HOME/Library/LaunchAgents/${PLIST_NAME}.plist"

case "${1:-install}" in
  install)
    # Stop old hook-based server if running
    if [ -f /tmp/zellij-claude-relay.pid ]; then
      OLD_PID=$(cat /tmp/zellij-claude-relay.pid 2>/dev/null)
      if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
        echo "Stopping old relay-server (PID $OLD_PID)..."
        kill "$OLD_PID" 2>/dev/null
        sleep 1
      fi
      rm -f /tmp/zellij-claude-relay.pid
    fi

    # Unload if already loaded
    launchctl bootout "gui/$(id -u)/${PLIST_NAME}" 2>/dev/null

    # Copy plist to LaunchAgents
    cp "$PLIST_SRC" "$PLIST_DST"
    echo "Installed: $PLIST_DST"

    # Load and start
    launchctl bootstrap "gui/$(id -u)" "$PLIST_DST"
    echo "Service started. Check: $0 status"
    ;;

  uninstall)
    launchctl bootout "gui/$(id -u)/${PLIST_NAME}" 2>/dev/null
    rm -f "$PLIST_DST"
    echo "Service uninstalled."
    ;;

  restart)
    launchctl kickstart -k "gui/$(id -u)/${PLIST_NAME}"
    echo "Service restarted."
    ;;

  status)
    if launchctl print "gui/$(id -u)/${PLIST_NAME}" 2>/dev/null | grep -q "pid ="; then
      PID=$(launchctl print "gui/$(id -u)/${PLIST_NAME}" 2>/dev/null | grep "pid =" | awk '{print $3}')
      echo "Running (PID $PID)"
    else
      echo "Not running"
    fi
    ;;

  logs)
    echo "=== stdout ==="
    tail -20 /tmp/relay-server.stdout.log 2>/dev/null || echo "(empty)"
    echo ""
    echo "=== stderr ==="
    tail -20 /tmp/relay-server.stderr.log 2>/dev/null || echo "(empty)"
    ;;

  *)
    echo "Usage: $0 [install|uninstall|restart|status|logs]"
    exit 1
    ;;
esac
