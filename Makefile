BACKEND_DIR  := backend
FRONTEND_DIR := frontend
SCRAPER_DIR  := scraper

PYTHON       := venv/bin/python
HEALTH_URL   := http://localhost:8080/api/races
BACKEND_LOG  := backend.log
WAIT_SECONDS := 120

.PHONY: frontend backend backend_noscrape clean setup clean_database

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
	if [ ! -x $(SCRAPER_DIR)/$(PYTHON) ]; then \
		echo "No Python venv in $(SCRAPER_DIR)/venv, run 'make setup' first"; exit 1; \
	fi; \
	(cd $(SCRAPER_DIR) && $(PYTHON) Scraper_Manager.py) || { echo "Scraper failed, stopping backend"; exit 1; }; \
	echo "Scraper finished. Backend still running, Ctrl+C to stop."; \
	wait $$PID

# 2b. Start Spring Boot in the foreground without running the scraper (Ctrl+C stops it)
backend_noscrape:
	cd $(BACKEND_DIR) && ./mvnw spring-boot:run

# 3. Create the scraper's Python venv and install its dependencies
setup:
	cd $(SCRAPER_DIR) && python3 -m venv venv && venv/bin/pip install -r requirements.txt

# 4. Remove every compiled Java class file
clean:
	find . -name "*.class" -not -path "*/node_modules/*" -delete

# 5. Delete every local H2 database file (refuses while the backend is running)
clean_database:
	@if curl -s -o /dev/null $(HEALTH_URL); then \
		echo "Backend is running on port 8080, stop it before cleaning the database"; exit 1; \
	fi
	find . \( -name "*.mv.db" -o -name "*.trace.db" -o -name "*.lock.db" \) \
		-not -path "*/node_modules/*" -not -path "*/venv/*" -print -delete
