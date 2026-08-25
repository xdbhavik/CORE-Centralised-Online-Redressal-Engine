#!/bin/sh
set -e

# Ensure the uploads directory is writable by the 'spring' user
# (Docker volumes mounted over /app/uploads are root-owned by default)
mkdir -p /app/uploads
chown -R spring:spring /app/uploads
chmod -R u+rwX /app/uploads

# Switch to the spring user and launch the app
# (-p preserves the environment / PATH so java is found)
exec su -p spring -s /bin/sh -c "/opt/java/openjdk/bin/java -jar /app/app.jar"
