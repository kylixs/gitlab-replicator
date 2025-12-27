#!/bin/bash

# Long-Running E2E Simulation Management Script

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="$ROOT_DIR/logs"
PID_FILE="$LOG_DIR/simulation.pid"
LOG_FILE="$LOG_DIR/simulation.log"

# Ensure log directory exists
mkdir -p "$LOG_DIR"

# Load environment variables
if [ -f "$ROOT_DIR/.env" ]; then
    source "$ROOT_DIR/.env"
fi

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;36m'
NC='\033[0m' # No Color

function print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

function print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

function print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

function print_error() {
    echo -e "${RED}✗${NC} $1"
}

function check_running() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            return 0  # Running
        else
            rm -f "$PID_FILE"
            return 1  # Not running
        fi
    fi
    return 1  # Not running
}

function start_simulation() {
    if check_running; then
        print_warning "Simulation is already running (PID: $(cat $PID_FILE))"
        return 1
    fi

    print_info "Starting long-running E2E simulation..."
    print_info "Log file: $LOG_FILE"

    cd "$ROOT_DIR"

    # Start simulation in background
    nohup npx ts-node "$SCRIPT_DIR/long-running-simulation.ts" > "$LOG_FILE" 2>&1 &
    PID=$!

    # Save PID
    echo "$PID" > "$PID_FILE"

    # Wait a moment and check if it's still running
    sleep 2

    if kill -0 "$PID" 2>/dev/null; then
        print_success "Simulation started successfully (PID: $PID)"
        print_info "View logs: tail -f $LOG_FILE"
        print_info "Stop simulation: $0 stop"
        return 0
    else
        print_error "Simulation failed to start. Check logs: $LOG_FILE"
        rm -f "$PID_FILE"
        return 1
    fi
}

function stop_simulation() {
    if ! check_running; then
        print_warning "Simulation is not running"
        return 1
    fi

    PID=$(cat "$PID_FILE")
    print_info "Stopping simulation (PID: $PID)..."

    # Send SIGTERM for graceful shutdown
    kill -TERM "$PID" 2>/dev/null

    # Wait up to 10 seconds for graceful shutdown
    for i in {1..10}; do
        if ! kill -0 "$PID" 2>/dev/null; then
            break
        fi
        sleep 1
    done

    # Force kill if still running
    if kill -0 "$PID" 2>/dev/null; then
        print_warning "Graceful shutdown failed, force killing..."
        kill -9 "$PID" 2>/dev/null
    fi

    rm -f "$PID_FILE"
    print_success "Simulation stopped"
}

function show_status() {
    print_info "Simulation Status"
    echo ""

    if check_running; then
        PID=$(cat "$PID_FILE")
        print_success "Status: Running (PID: $PID)"

        # Show process info
        echo ""
        echo "Process Info:"
        ps -p "$PID" -o pid,ppid,user,%cpu,%mem,etime,cmd 2>/dev/null || true

        # Show recent log
        if [ -f "$LOG_FILE" ]; then
            echo ""
            echo "Recent Log (last 20 lines):"
            echo "-----------------------------------------------------------"
            tail -20 "$LOG_FILE"
            echo "-----------------------------------------------------------"
        fi
    else
        print_warning "Status: Not running"

        # Show last log if exists
        if [ -f "$LOG_FILE" ]; then
            echo ""
            echo "Last Run Log (last 20 lines):"
            echo "-----------------------------------------------------------"
            tail -20 "$LOG_FILE"
            echo "-----------------------------------------------------------"
        fi
    fi

    echo ""
    print_info "Log file: $LOG_FILE"
}

function show_logs() {
    if [ -f "$LOG_FILE" ]; then
        tail -f "$LOG_FILE"
    else
        print_error "Log file not found: $LOG_FILE"
        return 1
    fi
}

function show_summary() {
    if [ ! -f "$LOG_FILE" ]; then
        print_error "Log file not found: $LOG_FILE"
        return 1
    fi

    print_info "Simulation Summary"
    echo ""

    # Extract statistics from log
    echo "Operations:"
    echo "  Commits:   $(grep -c "Created commit" "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  Branches:  $(grep -c "Created branch" "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  Tags:      $(grep -c "Created tag" "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  MRs:       $(grep -c "Created MR" "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  Merged:    $(grep -c "Merged MR" "$LOG_FILE" 2>/dev/null || echo 0)"
    echo "  Deleted:   $(grep -c "Deleted merged branch" "$LOG_FILE" 2>/dev/null || echo 0)"

    echo ""
    echo "Errors:    $(grep -c "\[ERROR\]" "$LOG_FILE" 2>/dev/null || echo 0)"

    # Show last status summary if available
    echo ""
    echo "Last Status Summary:"
    echo "-----------------------------------------------------------"
    grep -A 10 "STATUS SUMMARY" "$LOG_FILE" | tail -12 || echo "No status summary found"
    echo "-----------------------------------------------------------"
}

function show_help() {
    cat << EOF
Long-Running E2E Simulation Management

Usage: $0 <command>

Commands:
  start     Start the simulation
  stop      Stop the simulation gracefully
  status    Show simulation status
  logs      Show logs in real-time (tail -f)
  summary   Show operation summary
  restart   Restart the simulation
  help      Show this help message

Examples:
  $0 start              # Start the simulation
  $0 stop               # Stop the simulation
  $0 status             # Check if running
  $0 logs               # Watch logs in real-time
  $0 summary            # Show statistics

Files:
  PID file: $PID_FILE
  Log file: $LOG_FILE

For more information, see tests/e2e/README.md
EOF
}

# Main
case "${1:-help}" in
    start)
        start_simulation
        ;;
    stop)
        stop_simulation
        ;;
    restart)
        stop_simulation
        sleep 2
        start_simulation
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    summary)
        show_summary
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        print_error "Unknown command: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
