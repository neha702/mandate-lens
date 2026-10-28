# Load local-only values from the ignored repository-root .env file when present.
if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

export DATABASE_URL=${DATABASE_URL:-jdbc:postgresql://localhost:5432/mandatelens}
export DATABASE_USERNAME=${DATABASE_USERNAME:-postgres}
export DATABASE_PASSWORD=${DATABASE_PASSWORD:-postgres}
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-storesync-simulator,agentguard-deterministic}
export SERVER_ERROR_INCLUDE_MESSAGE=${SERVER_ERROR_INCLUDE_MESSAGE:-always}
