#!/usr/bin/env bash
set -euo pipefail
# Prepare the existing isolated checkout; do not create a worktree or edit tracked files.
cd /workspace/morphe-patches
task_env=/workspace/.morphe-env
mkdir -p "$task_env/downloads" "$task_env/gradle" "$task_env/android-sdk/cmdline-tools"
# adb requires this standard user directory even when ANDROID_USER_HOME is set.
mkdir -p "$HOME/.android"

fetch_verified() {
  local target="$1" url="$2" algorithm="$3" digest="$4"
  if [ ! -f "$target" ]; then
    curl -fsSL --retry 2 "$url" -o "$target.partial"
    mv "$target.partial" "$target"
  fi
  python3 - "$target" "$algorithm" "$digest" <<'PY'
import hashlib, sys
with open(sys.argv[1], 'rb') as stream:
    actual = hashlib.file_digest(stream, sys.argv[2]).hexdigest()
if actual != sys.argv[3]:
    raise SystemExit('Checksum mismatch: ' + sys.argv[1])
PY
}
if [ ! -x "$task_env/jdk-21.0.12.1/bin/javac" ]; then
  fetch_verified "$task_env/downloads/jdk.tar.gz" \
    https://download.oracle.com/java/21/archive/jdk-21.0.12.1_linux-x64_bin.tar.gz \
    sha256 12f870b21301b42292558a3f872ce543affa2b86cb6458591c78388c41ddb111
  tar -xzf "$task_env/downloads/jdk.tar.gz" -C "$task_env"
fi
ln -sfn jdk-21.0.12.1 "$task_env/jdk"
if [ ! -x "$task_env/android-sdk/cmdline-tools/19.0/bin/sdkmanager" ]; then
  # Checksum published by Google's HTTPS SDK repository metadata.
  fetch_verified "$task_env/downloads/cmdline-tools-19.zip" \
    https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
    sha1 5fdcc763663eefb86a5b8879697aa6088b041e70
  task_unpack=$(mktemp -d "$task_env/downloads/tools.XXXXXX")
  unzip -q "$task_env/downloads/cmdline-tools-19.zip" -d "$task_unpack"
  mv "$task_unpack/cmdline-tools" "$task_env/android-sdk/cmdline-tools/19.0"
  rmdir "$task_unpack"
fi
cat > "$task_env/activate.sh" <<'ACTIVATE'
# Source this file before running the repository's Gradle wrapper.
export JAVA_HOME=/workspace/.morphe-env/jdk
export ANDROID_HOME=/workspace/.morphe-env/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME=/workspace/.morphe-env/android-user
export GRADLE_USER_HOME=/workspace/.morphe-env/gradle
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/19.0/bin:$ANDROID_HOME/platform-tools:$PATH"
# Refresh the trusted platform certificate after snapshot restoration.
cp /etc/ssl/certs/java/cacerts /workspace/.morphe-env/cacerts
if [ -f /usr/local/share/ca-certificates/environment-proxy-ca.crt ]; then
  "$JAVA_HOME/bin/keytool" -importcert -noprompt -alias morphe-environment-proxy \
    -file /usr/local/share/ca-certificates/environment-proxy-ca.crt \
    -keystore /workspace/.morphe-env/cacerts -storepass changeit >/dev/null
fi
_morphe_proxy_options=$(python3 - <<'PY'
import os
from urllib.parse import urlparse
p = urlparse(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or '')
if p.hostname:
    assert not p.username and not p.password, 'Authenticated proxy requires supported credential configuration'
    print(f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port or 80} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port or 80}')
PY
)
export JAVA_OPTS="-Djava.util.prefs.userRoot=/workspace/.morphe-env/java-prefs -Djavax.net.ssl.trustStore=/workspace/.morphe-env/cacerts $_morphe_proxy_options"
export GRADLE_OPTS="$JAVA_OPTS"
unset _morphe_proxy_options
ACTIVATE
source "$task_env/activate.sh"
python3 - <<'PY'
import os, subprocess
from urllib.parse import urlparse
p = urlparse(os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy') or '')
cmd = ['sdkmanager', '--sdk_root=' + os.environ['ANDROID_HOME']]
if p.hostname:
    cmd += ['--proxy=http', '--proxy_host=' + p.hostname, '--proxy_port=' + str(p.port or 80)]
with open('/workspace/.morphe-env/sdk-install.log', 'w') as log:
    result = subprocess.run(cmd + ['platforms;android-36', 'build-tools;36.0.0', 'platform-tools'],
                            input='y\n' * 100, text=True, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    raise SystemExit('SDK installation failed; inspect /workspace/.morphe-env/sdk-install.log')
PY
java -version
javac -version
./gradlew --version
sdkmanager --sdk_root="$ANDROID_HOME" --list_installed
"$ANDROID_HOME/build-tools/36.0.0/aapt2" version
adb version

# Compile and dex an existing Android-free extension class to verify the toolchain.
mkdir -p "$task_env/smoke/classes" "$task_env/smoke/dex"
javac --release 11 -d "$task_env/smoke/classes" \
  extensions/music/src/main/java/app/morphe/extension/music/patches/jam/QueueCommand.java
"$ANDROID_HOME/build-tools/36.0.0/d8" --release --min-api 26 \
  --lib "$ANDROID_HOME/platforms/android-36/android.jar" --output "$task_env/smoke/dex" \
  "$task_env/smoke/classes/app/morphe/extension/music/patches/jam/QueueCommand.class"
test -s "$task_env/smoke/dex/classes.dex"

# Toolchain-only mode is for diagnosis when registry access is missing.
# It does not establish that the repository build or tests pass.
if [ "${MORPHE_SETUP_TOOLCHAIN_ONLY:-0}" = 1 ]; then
  printf 'Toolchain verified; repository build/tests remain unvalidated.\n'
  exit 0
fi
: "${GITHUB_TOKEN:?Supply GITHUB_TOKEN securely in environment settings with read:packages access to MorpheApp/registry}"
export GITHUB_ACTOR="${GITHUB_ACTOR:-CaptKraken}"
./gradlew :patches:buildAndroid :patches:test --no-daemon --max-workers=4 --console=plain
# Inspect current XML reports under patches/build/test-results/test; APK-dependent
# regression tests are expected to skip unless -PjamApk=/absolute/path/to/ytm.apk is supplied.
git status --short
