.PHONY: run-server run-storefront
run-server:
	@/bin/bash -c 'source ./local-env.sh && exec ./mvnw -pl src/server spring-boot:run'

run-storefront:
	@/bin/bash -c 'if [ ! -d storefront/node_modules ]; then npm --prefix storefront ci || exit; fi; exec npm --prefix storefront run dev -- --host 127.0.0.1'
