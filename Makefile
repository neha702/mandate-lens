.PHONY: run-server run-mandatelens run-merchant run-storefront run-pulse-ui

run-server: run-mandatelens

run-mandatelens:
	@/bin/bash -c 'source ./local-env.sh && export SPRING_PROFILES_ACTIVE=mandatelens-server,agentguard-deterministic && exec ./mvnw -pl src/server spring-boot:run -Dspring-boot.run.main-class=com.emeralds.mandatelens.MandateLensApplication'

run-merchant:
	@/bin/bash -c 'source ./local-env.sh && export SPRING_PROFILES_ACTIVE=merchant-server,storesync-simulator && exec ./mvnw -pl src/server -Pmerchant-app spring-boot:run'

run-storefront:
	@/bin/bash -c 'if [ ! -d storefront/node_modules ]; then npm --prefix storefront ci || exit; fi; exec npm --prefix storefront run dev -- --host 127.0.0.1'

run-pulse-ui:
	@/bin/bash -c 'if [ ! -d mandate-lens-ui/node_modules ]; then npm --prefix mandate-lens-ui ci || exit; fi; exec npm --prefix mandate-lens-ui run dev -- --host 127.0.0.1'
