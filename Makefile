BACKEND_DIR  := backend
FRONTEND_DIR := frontend
SCRAPER_DIR  := scraper

PYTHON       := venv/bin/python
HEALTH_URL   := http://localhost:8080/api/races
BACKEND_LOG  := backend.log
WAIT_SECONDS := 120

.PHONY: frontend backend clean

# 1. Frontend dev server (Vite)
frontend:
	cd $(FRONTEND_DIR) && npm run dev

# 2. Start Spring Boot in the background, wait until it answers, run the scraper,
#    then keep the backend running in the foreground (Ctrl+C stops it).
backend:
	@(cd $(BACKEND_DIR) && exec ./mvnw spring-boot:run) > $(BACKEND_LOG) 2>&1 & \
	PID=$$!; \
	cleanup() { kill $$PID 2>/dev/null; fuser -k -s 8080/tcp 2>/dev/null; }; \
	trap cleanup EXIT; \
	trap 'exit 130' INT TERM; \
	echo "Starting backend (pid $$PID, log in $(BACKEND_LOG))..."; \
	for i in $$(seq $(WAIT_SECONDS)); do \
		if curl -s -o /dev/null -f $(HEALTH_URL); then break; fi; \
		if ! kill -0 $$PID 2>/dev/null; then \
			echo "Backend crashed, last lines of $(BACKEND_LOG):"; tail -n 30 $(BACKEND_LOG); exit 1; \
		fi; \
		sleep 1; \
	done; \
	if ! curl -s -o /dev/null -f $(HEALTH_URL); then \
		echo "Backend did not answer after $(WAIT_SECONDS)s"; exit 1; \
	fi; \
	echo "Backend is up, running the scraper..."; \
	cd $(SCRAPER_DIR) && $(PYTHON) Scraper_Manager.py; \
	echo "Scraper finished. Backend still running, Ctrl+C to stop."; \
	wait $$PID

# 3. Remove every compiled Java class file
clean:
	find . -name "*.class" -not -path "*/node_modules/*" -delete
