.PHONY: run-server run-mandatelens run-merchant run-merchant-ui run-agentpulse-ui

run-server: run-mandatelens

run-mandatelens:
	@/bin/bash -c 'source ./local-env.sh && export SPRING_PROFILES_ACTIVE=mandatelens-server,agentguard-deterministic && exec ./mvnw -pl src/server spring-boot:run -Dspring-boot.run.main-class=com.emeralds.mandatelens.MandateLensApplication'

run-merchant:
	@/bin/bash -c 'source ./local-env.sh && export SPRING_PROFILES_ACTIVE=merchant-server,storesync-simulator && exec ./mvnw -pl src/server -Pmerchant-app spring-boot:run'

run-merchant-ui:
	@/bin/bash -c 'if [ ! -d merchant-ui/node_modules ]; then npm --prefix merchant-ui ci || exit; fi; exec npm --prefix merchant-ui run dev -- --host 127.0.0.1'

run-agentpulse-ui:
	@/bin/bash -c 'if [ ! -d agentpulse-ui/node_modules ]; then npm --prefix agentpulse-ui ci || exit; fi; exec npm --prefix agentpulse-ui run dev -- --host 127.0.0.1'
