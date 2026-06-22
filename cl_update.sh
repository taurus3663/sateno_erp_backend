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
# 2. UPLOAD (с временно име, докато старото работи)
# -------------------------
echo ">>> 2) Качване на сървъра (временен файл)..."
scp -P "$SERVER_PORT" "$LOCAL_JAR" "$SERVER_USER@$SERVER_HOST:$SERVER_DIR/${JAR_NAME}.new" || exit 1

# -------------------------
# 3. DEPLOY (спиране → атомарна замяна → старт)
# -------------------------
echo ">>> 3) Замяна и рестартиране..."
ssh -p "$SERVER_PORT" "$SERVER_USER@$SERVER_HOST" << 'EOF'
  JAR_NAME="sateno_b-0.0.1-SNAPSHOT.jar"
  SERVER_DIR="/home/erp-b-sateno"
  cd $SERVER_DIR

  echo ">>> Graceful спиране (изчакване на активни задачи, макс. 10 мин)..."
  sudo systemctl stop sateno-backend.service || true

  # Изчакваме портът да се освободи — макс 600 секунди (10 минути)
  WAITED=0
  while fuser 9494/tcp > /dev/null 2>&1; do
    if [ $WAITED -ge 600 ]; then
      echo ">>> Таймаут 10 мин! Force-kill..."
      fuser -k 9494/tcp || true
      pgrep -f "$JAR_NAME" | xargs kill -9 2>/dev/null || true
      break
    fi
    echo ">>> Чакам процесът да приключи... ($WAITED / 600 сек)"
    sleep 5
    WAITED=$((WAITED + 5))
  done

  echo ">>> Атомарна замяна на JAR..."
  mv -f ${JAR_NAME}.new ${JAR_NAME}

  echo ">>> Стартиране на сървиса..."
  sudo systemctl daemon-reload
  sudo systemctl start sateno-backend.service

  echo ">>> Проверка на статуса..."
  sleep 3
  systemctl status sateno-backend.service --no-pager

EOF

echo ">>> DONE ✔"