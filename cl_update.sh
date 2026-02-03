#!/bin/bash

# Проверка за аргументи
if [ -z "$1" ]; then
  echo "Употреба: ./deploy_erp.sh user@host -p port"
  exit 1
fi

RAW_TARGET="$1"
SERVER_PORT="22" # По подразбиране

# Проверка дали е подаден порт с -p (както в твоя пример)
if [ "$2" == "-p" ] && [ ! -z "$3" ]; then
  SERVER_PORT="$3"
fi

# Екстрактване на потребител и хост
SERVER_USER=$(echo "$RAW_TARGET" | cut -d@ -f1)
SERVER_HOST=$(echo "$RAW_TARGET" | cut -d@ -f2)

# Настройки на папките
SERVER_DIR="/home/erp-b-sateno"
JAR_NAME="sateno_b-0.0.1-SNAPSHOT.jar"

echo ">>> Цел: $SERVER_USER@$SERVER_HOST на порт $SERVER_PORT"

# -------------------------
# 1. BUILD (Локално)
# -------------------------
echo ">>> 1) Билдване на JAR..."
mvn clean package -DskipTests || exit 1

LOCAL_JAR="target/$JAR_NAME"

# -------------------------
# 2. UPLOAD
# -------------------------
echo ">>> 2) Качване на сървъра през порт $SERVER_PORT..."
scp -P "$SERVER_PORT" "$LOCAL_JAR" "$SERVER_USER@$SERVER_HOST:$SERVER_DIR/" || exit 1

# -------------------------
# 3. DEPLOY (На сървъра)
# -------------------------
echo ">>> 3) Рестартиране на приложението..."
ssh -p "$SERVER_PORT" "$SERVER_USER@$SERVER_HOST" << EOF
  cd $SERVER_DIR

  echo ">>> Спиране на системния сървис (за всеки случай)..."
    sudo systemctl stop sateno-backend.service || true

 echo ">>> Почистване на забила Java..."
   fuser -k 9494/tcp || true
   pgrep -f $JAR_NAME | xargs kill -9 2>/dev/null || true
  sleep 2

echo ">>> Рестартиране на приложението чрез Systemd..."
  sudo systemctl daemon-reload
  sudo systemctl start sateno-backend.service

  echo ">>> Проверка на статуса..."
  sleep 3
  systemctl status sateno-backend.service --no-pager

EOF

echo ">>> DONE ✔"