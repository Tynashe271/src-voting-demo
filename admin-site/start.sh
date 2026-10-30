#!/bin/sh
set -e
touch database/database.sqlite
php artisan migrate --force
exec php artisan serve --host=0.0.0.0 --port=8000
