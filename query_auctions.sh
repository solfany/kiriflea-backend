#!/bin/bash
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi
mysql -h "$DB_HOST" -u "$DB_USERNAME" -p"$DB_PASSWORD" market -e "SELECT COUNT(*) FROM auctions WHERE end_at < NOW() AND status = 'ACTIVE';"
