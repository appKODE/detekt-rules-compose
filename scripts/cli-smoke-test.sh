#!/usr/bin/env bash
# Runs the published jars through the real detekt CLIs on smoke/sample and checks that
#  - detekt-cli 1.23.8 + detekt1 jar and detekt-cli 2.x + detekt2 jar report exactly smoke/expected-findings.txt
#  - the detekt1 jar still runs on the oldest detekt-cli 1.x that 1.4.0 ran on
#  - the 1.4.0 release jar reports the same on detekt-cli 1.23.8 (golden behaviour; skip with --no-golden)
set -euo pipefail
cd "$(dirname "$0")/.."

DETEKT1_CLI=1.23.8
DETEKT1_OLDEST_CLI=1.22.0
DETEKT2_CLI=$(sed -n 's/^detekt2 *= *"\(.*\)"/\1/p' gradle/libs.versions.toml)
GOLDEN_JAR_VERSION=1.4.0
# rules added after $GOLDEN_JAR_VERSION, as an ERE alternation (A|B)
NEW_SINCE_GOLDEN="UnnecessaryLayoutWrapper"
MAVEN=https://repo1.maven.org/maven2
OUT=build/smoke
mkdir -p "$OUT"

fetch() { # <maven path> -> prints local file
  local file="$OUT/${1##*/}"
  [ -f "$file" ] || curl -sfL -o "$file" "$MAVEN/$1"
  echo "$file"
}

# checkstyle XML -> "File.kt:line:column RuleId message", sorted
normalize() {
  sed -n -e 's#.*<file name=".*/\([^/"]*\)".*#file \1#p' \
    -e 's/.*line="\([0-9]*\)" column="\([0-9]*\)" severity="[a-z]*" message="\([^"]*\)" source="detekt\.\([A-Za-z]*\)".*/\1:\2 \4 \3/p' "$1" |
    awk '/^file /{f=$2; next} {print f ":" $0}' | sort -t: -k1,1 -k2,2n -k3,3n
}

run() { # <name> <cli jar> <plugin jar> [extra cli args...]
  local name=$1 cli=$2 plugin=$3; shift 3
  # exit code 2 = findings reported, which is what we want here
  java -jar "$cli" --input smoke/sample --config smoke/all-rules.yml --disable-default-rulesets \
    --plugins "$plugin" --classpath "$stdlib" --jvm-target 11 --report "$@" > "$OUT/$name.log" 2>&1 || [ $? -eq 2 ] \
    || { cat "$OUT/$name.log"; exit 1; }
}

check() { # <name> <checkstyle xml> [expected findings]
  normalize "$2" > "$OUT/$1.findings"
  if diff -u "${3:-smoke/expected-findings.txt}" "$OUT/$1.findings"; then echo "OK   $1"; else echo "FAIL $1"; failed=1; fi
}

./gradlew -q :detekt1:jar :detekt2:jar
stdlib=$(fetch org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar)
cli1=$(fetch "io/gitlab/arturbosch/detekt/detekt-cli/$DETEKT1_CLI/detekt-cli-$DETEKT1_CLI-all.jar")
cli1_oldest=$(fetch "io/gitlab/arturbosch/detekt/detekt-cli/$DETEKT1_OLDEST_CLI/detekt-cli-$DETEKT1_OLDEST_CLI-all.jar")
cli2=$(fetch "dev/detekt/detekt-cli/$DETEKT2_CLI/detekt-cli-$DETEKT2_CLI-all.jar")
failed=0

run detekt1 "$cli1" detekt1/build/libs/detekt1.jar "xml:$OUT/detekt1.xml"
check "detekt1 (detekt-cli $DETEKT1_CLI)" "$OUT/detekt1.xml"

run detekt1-oldest "$cli1_oldest" detekt1/build/libs/detekt1.jar "xml:$OUT/detekt1-oldest.xml"
check "detekt1 (detekt-cli $DETEKT1_OLDEST_CLI)" "$OUT/detekt1-oldest.xml"

run detekt2 "$cli2" detekt2/build/libs/detekt2.jar "checkstyle:$OUT/detekt2.xml" --analysis-mode full
check "detekt2 (detekt-cli $DETEKT2_CLI)" "$OUT/detekt2.xml"

if [ "${1:-}" != "--no-golden" ]; then
  golden=$(fetch "ru/kode/detekt-rules-compose/$GOLDEN_JAR_VERSION/detekt-rules-compose-$GOLDEN_JAR_VERSION.jar")
  run golden "$cli1" "$golden" "xml:$OUT/golden.xml"
  # rules added after $GOLDEN_JAR_VERSION are not in the golden jar
  grep -vE " ($NEW_SINCE_GOLDEN) " smoke/expected-findings.txt > "$OUT/golden-expected.txt"
  check "golden $GOLDEN_JAR_VERSION (detekt-cli $DETEKT1_CLI)" "$OUT/golden.xml" "$OUT/golden-expected.txt"
fi

exit $failed
