#!/usr/bin/env bash
set -euo pipefail

RED="\033[31m"; GREEN="\033[32m"; YELLOW="\033[33m"; BLUE="\033[34m"; MAGENTA="\033[35m"; CYAN="\033[36m"; BOLD="\033[1m"; DIM="\033[2m"; RESET="\033[0m"

echo -e "${CYAN}Building all projects...${RESET}" >&2
./mvnw -B -q verify > /dev/null 2>&1
echo -e "${GREEN}Build complete.${RESET}" >&2

die() { echo "[ERROR] $*" >&2; exit 1; }

resolve_one() {
	# $1 = directory, $2 = glob pattern
	local dir=$1
	local pattern=$2
	local result
	# Use find to avoid literal glob fallback; pick first match only
	result=$(find "$dir" -maxdepth 1 -type f -name "$pattern" | head -n1 || true)
	[ -n "$result" ] || die "No jar found matching $dir/$pattern"
	echo "$result"
}

SERVER_JAR=$(resolve_one server/target '*-shaded.jar')
AGENT_JAR=$(resolve_one agent/target '*-shaded.jar')
JAR_APP=$(resolve_one sample-spring-app/target 'sample-spring-app-*.jar')

echo -e "${CYAN}Using jars:${RESET}" >&2
echo -e "  Server: ${DIM}$SERVER_JAR${RESET}" >&2
echo -e "  Agent : ${DIM}$AGENT_JAR${RESET}" >&2
echo -e "  App   : ${DIM}$JAR_APP${RESET}" >&2

# start the server in the background and send output to dev null
java -jar "$SERVER_JAR" > /dev/null 2>&1 &
SERVER_PID=$!

trap 'echo "Stopping server..."; kill $SERVER_PID 2>/dev/null || true; wait $SERVER_PID 2>/dev/null || true' EXIT INT TERM

# give the server some time to start
sleep 3

now_ms() { perl -MTime::HiRes=time -e 'printf("%d\n", time()*1000)'; }

declare -a SUMMARY_LABELS=()
declare -a SUMMARY_WALL=()
declare -a SUMMARY_STARTUP=()

run_case() {
	local label=$1
	shift
	local tmp
	tmp=$(mktemp -t jlib-bench-XXXX.out)
	local start end dur status
	start=$(now_ms)
	# Run the Java command capturing all output
	java "$@" > "$tmp" 2>&1 || status=$? || true
	end=$(now_ms)
	dur=$(( end - start ))
	# Extract Spring Boot startup line (first occurrence)
	local line raw_startup_ms startup_ms="" process_ms=""
	line=$(grep -F 'Started DemoApplication in ' "$tmp" | head -n1 || true)
	if [ -n "$line" ]; then
		# Parse 'Started DemoApplication in X.YYY seconds (process running for Z.ZZZ)'
		if [[ $line =~ Started\ DemoApplication\ in\ ([0-9]+\.[0-9]+)\ seconds.*process\ running\ for\ ([0-9]+\.[0-9]+) ]]; then
			startup_ms=$(perl -e "printf('%.0f', ${BASH_REMATCH[1]} * 1000)")
			process_ms=$(perl -e "printf('%.0f', ${BASH_REMATCH[2]} * 1000)")
		fi
	fi

	echo
	echo -e "${BOLD}=== ${label} ===${RESET}"
	echo -e "Wall: ${YELLOW}${dur} ms${RESET}"
	if [ -n "$line" ]; then
		if [ -n "$startup_ms" ]; then
			echo -e "Startup: ${GREEN}${startup_ms} ms${RESET}  (process ${DIM}${process_ms} ms${RESET})"
		else
			echo -e "Startup: ${GREEN}$line${RESET}"
		fi
	else
		echo -e "Startup: ${RED}(line not found)${RESET}"
	fi

	SUMMARY_LABELS+=("$label")
	SUMMARY_WALL+=("$dur")
	SUMMARY_STARTUP+=("${startup_ms:-}")

	rm -f "$tmp"
}

run_case "1) agent with server:8080" -javaagent:"$AGENT_JAR"=server:8080 -jar "$JAR_APP"
run_case "2) local agent only" -javaagent:"$AGENT_JAR" -jar "$JAR_APP"
run_case "3) no agent" -jar "$JAR_APP"

echo
echo -e "${MAGENTA}${BOLD}Summary:${RESET}"
printf "%-28s %12s %14s\n" "Case" "Wall(ms)" "Startup(ms)"
printf "%-28s %12s %14s\n" "----------------------------" "--------" "-----------"
for i in "${!SUMMARY_LABELS[@]}"; do
	label=${SUMMARY_LABELS[$i]}
	wall=${SUMMARY_WALL[$i]}
	startup=${SUMMARY_STARTUP[$i]:-?}
	printf "%-28s %12s %14s\n" "$label" "$wall" "$startup"
done

echo

echo -e "${CYAN}Benchmark complete.${RESET}" >&2