.PHONY: run-server run-storefront
run-server:
	@/bin/bash -c 'source ./local-env.sh && exec ./mvnw -pl src/server spring-boot:run'

run-storefront:
	@/bin/bash -c 'if [ ! -d storefront/node_modules ]; then npm --prefix storefront ci || exit; fi; export VITE_API_URL=http://localhost:8080; exec npm --prefix storefront run dev -- --host 127.0.0.1'
