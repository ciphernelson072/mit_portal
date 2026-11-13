#!/bin/sh
set -eu

ACTION=${1:-status}
PORTAL_HOME=${MIT_PORTAL_HOME:-"$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"}
ENV_FILE=${MIT_PORTAL_ENV_FILE:-"$HOME/.config/mit-portal/serv00.env"}
RUN_DIR=${MIT_PORTAL_RUN_DIR:-"$HOME/.local/run/mit-portal"}
PID_FILE="$RUN_DIR/portal.pid"
LOG_FILE="$PORTAL_HOME/logs/portal.log"
APP_JAR="$PORTAL_HOME/app.jar"

fail() {
    printf '%s\n' "MIT Portal: $*" >&2
    exit 1
}

read_pid() {
    if [ ! -f "$PID_FILE" ]; then
        return 1
    fi
    PID=$(cat "$PID_FILE")
    case "$PID" in
        ''|*[!0-9]*) fail "Invalid PID file at $PID_FILE; inspect it before removing it." ;;
    esac
    return 0
}

is_portal_pid() {
    PID_TO_CHECK=$1
    kill -0 "$PID_TO_CHECK" 2>/dev/null || return 1
    COMMAND_LINE=$(ps -p "$PID_TO_CHECK" -o command= 2>/dev/null || true)
    case "$COMMAND_LINE" in
        *"$APP_JAR"*) return 0 ;;
        *) return 1 ;;
    esac
}

stop_portal() {
    if ! read_pid; then
        printf '%s\n' "MIT Portal is not running (no PID file)."
        return 0
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
        rm -f "$PID_FILE"
        printf '%s\n' "Removed stale MIT Portal PID file."
        return 0
    fi
    if ! is_portal_pid "$PID"; then
        fail "PID $PID is not the MIT Portal process; refusing to stop it."
    fi

    kill -TERM "$PID"
    attempt=0
    while [ "$attempt" -lt 30 ]; do
        if ! kill -0 "$PID" 2>/dev/null; then
            rm -f "$PID_FILE"
            printf '%s\n' "MIT Portal stopped."
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    fail "MIT Portal did not stop within 30 seconds; inspect $LOG_FILE before retrying."
}

case "$ACTION" in
    start|restart)
        if [ "$ACTION" = restart ]; then
            stop_portal
        fi
        [ -f "$ENV_FILE" ] || fail "Missing private environment file $ENV_FILE. Copy serv00.env.example there and configure it."
        [ -r "$ENV_FILE" ] || fail "Cannot read private environment file $ENV_FILE."
        set -a
        . "$ENV_FILE"
        set +a

        [ "${SPRING_PROFILES_ACTIVE:-}" = prod ] || fail "SPRING_PROFILES_ACTIVE must be prod."
        [ "${SERVER_ADDRESS:-}" = 127.0.0.1 ] || fail "SERVER_ADDRESS must be 127.0.0.1 behind the Serv00 proxy."
        [ "${SERVER_PORT:-}" -ge 1024 ] 2>/dev/null || fail "SERVER_PORT must be a reserved TCP port from 1024 through 64000."
        [ "${SERVER_PORT:-}" -le 64000 ] 2>/dev/null || fail "SERVER_PORT must be a reserved TCP port from 1024 through 64000."
        [ "${DB_USER:-}" ] || fail "DB_USER is required."
        [ "$DB_USER" != root ] || fail "Use the Serv00 application database user, not root."
        [ "${DB_PASSWORD:-}" ] || fail "DB_PASSWORD is required."
        [ "${JWT_SECRET:-}" ] || fail "JWT_SECRET is required."
        [ "${PAYMENT_WEBHOOK_SECRET:-}" ] || fail "PAYMENT_WEBHOOK_SECRET is required."
        [ "${FRONTEND_URL:-}" ] || fail "FRONTEND_URL is required."
        [ "${CORS_ALLOWED_ORIGINS:-}" = "$FRONTEND_URL" ] || fail "For this same-origin deployment, CORS_ALLOWED_ORIGINS must exactly match FRONTEND_URL."
        case "$FRONTEND_URL" in
            https://*) ;;
            *) fail "FRONTEND_URL must use HTTPS." ;;
        esac
        [ "${SAMPLE_DATA_ENABLED:-false}" = false ] || fail "SAMPLE_DATA_ENABLED must be false."
        [ "${FLYWAY_BASELINE_ON_MIGRATE:-false}" = false ] || fail "FLYWAY_BASELINE_ON_MIGRATE must be false."
        [ "${MAIL_ENABLED:-false}" = false ] || fail "Keep MAIL_ENABLED=false until an approved provider is configured."
        [ "${UPLOAD_DIR:-}" ] || fail "UPLOAD_DIR is required."
        case "$UPLOAD_DIR" in
            "$HOME"/*) ;;
            *) fail "UPLOAD_DIR must be a persistent directory beneath your Serv00 home directory." ;;
        esac
        [ -r "$APP_JAR" ] || fail "Application JAR is missing or unreadable: $APP_JAR"
        command -v "${JAVA_BIN:-java17}" >/dev/null 2>&1 || fail "Java 17 command '${JAVA_BIN:-java17}' is unavailable; enable Serv00 Binexec and Java 17."
        command -v curl >/dev/null 2>&1 || fail "curl is required for the local health check."

        mkdir -p "$RUN_DIR" "$PORTAL_HOME/logs" "$UPLOAD_DIR"
        chmod 700 "$RUN_DIR" "$UPLOAD_DIR"
        if read_pid; then
            if is_portal_pid "$PID"; then
                printf '%s\n' "MIT Portal is already running with PID $PID."
                exit 0
            fi
            if kill -0 "$PID" 2>/dev/null; then
                fail "PID $PID is in use by another process; refusing to start."
            fi
            rm -f "$PID_FILE"
        fi

        JAVA_BIN=${JAVA_BIN:-java17}
        JAVA_OPTS=${JAVA_OPTS:--Xms96m -Xmx384m -XX:MaxMetaspaceSize=192m}
        # JAVA_OPTS is intentionally word-split into JVM arguments.
        # shellcheck disable=SC2086
        nohup "$JAVA_BIN" $JAVA_OPTS -jar "$APP_JAR" \
            >> "$LOG_FILE" 2>&1 </dev/null &
        PID=$!
        printf '%s\n' "$PID" > "$PID_FILE"
        attempt=0
        while [ "$attempt" -lt 60 ]; do
            if ! is_portal_pid "$PID"; then
                rm -f "$PID_FILE"
                fail "Application exited during startup; inspect $LOG_FILE."
            fi
            if curl -fsS --max-time 3 "http://127.0.0.1:$SERVER_PORT/actuator/health" >/dev/null; then
                printf '%s\n' "MIT Portal is healthy on loopback port $SERVER_PORT (PID $PID)."
                exit 0
            fi
            attempt=$((attempt + 1))
            sleep 2
        done
        fail "Health check did not pass within 120 seconds; inspect $LOG_FILE."
        ;;
    stop)
        stop_portal
        ;;
    status)
        if read_pid && is_portal_pid "$PID"; then
            printf '%s\n' "MIT Portal process is running (PID $PID)."
            exit 0
        fi
        printf '%s\n' "MIT Portal process is not running."
        exit 1
        ;;
    *)
        fail "Usage: $0 {start|stop|restart|status}"
        ;;
esac
