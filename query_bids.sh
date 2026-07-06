#!/bin/bash
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi
mysql -h "$DB_HOST" -u "$DB_USERNAME" -p"$DB_PASSWORD" market -e "SELECT id, auction_id, bidder_id, amount FROM bids;"
