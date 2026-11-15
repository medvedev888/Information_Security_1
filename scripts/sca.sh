#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target/tools
case "$(uname -s)/$(uname -m)" in
  Darwin/arm64) platform=darwin_arm64; checksum=d4926ef8da134332543be276dff157e4c170f99ad91fc48e89448a8816bdf70b ;;
  Linux/x86_64) platform=linux_amd64; checksum=160c6253e2922e9e0a6a9771c5c4c40a4b3c6090b34a3cdd5e80c96cc2e4218b ;;
  *) echo 'Supported platforms: macOS arm64, Linux x86_64' >&2; exit 2 ;;
esac
scanner=target/tools/osv-scanner
if [ ! -f "$scanner" ]; then
  curl -fsSL --retry 3 "https://github.com/google/osv-scanner/releases/download/v2.7.0/osv-scanner_$platform" -o "$scanner"
fi
printf '%s  %s\n' "$checksum" "$scanner" | shasum -a 256 -c -
chmod +x "$scanner"
# Maven exports resolved direct and transitive dependencies, not just pom declarations.
# Retry only transient repository failures; analysis findings are never ignored.
for attempt in 1 2 3; do
  status=0
  mvn -B -U org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
    -DoutputFormat=json -DoutputName=bom.cdx > target/sca-maven.log 2>&1 || status=$?
  cat target/sca-maven.log
  if [ "$status" -eq 0 ]; then break; fi
  if [ "$attempt" -eq 3 ] || ! grep -Eq '(status code: (429|502|503|504)|Too Many Requests|Connection reset|Read timed out)' target/sca-maven.log; then
    exit "$status"
  fi
  echo "Temporary Maven repository failure; retry $((attempt + 1))/3."
  sleep "$((attempt * 15))"
done
cp target/sca-maven.log target/sca.log
result=0
for format in json html; do
  status=0
  "$scanner" scan source --format "$format" --output-file "target/sca-report.$format" --lockfile target/bom.cdx.json >> target/sca.log 2>&1 || status=$?
  if [ "$status" -ne 0 ]; then result="$status"; fi
done
cat target/sca.log
if [ "$result" -eq 0 ]; then
  echo 'SCA PASS: no known vulnerabilities found in resolved dependencies.'
else
  echo 'SCA FAILED: inspect target/sca-report.html and target/sca.log.' >&2
fi
exit "$result"
