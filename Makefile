BACKEND_DIR  := backend
FRONTEND_DIR := frontend
SCRAPER_DIR  := scraper

PYTHON          := venv/bin/python
PORT            := 8080
HEALTH_URL      := http://localhost:$(PORT)/api/races
BACKEND_LOG     := backend.log
WAIT_SECONDS    := 120
SCRAPE_INTERVAL := 259200
SCRAPE_TIMEOUT  := 2h
SCRAPE_STAMP    := .last_scrape

.PHONY: frontend backend backend_noscrape clean setup clean_database

# 1. Frontend dev server (Vite)
frontend:
	cd $(FRONTEND_DIR) && npm run dev

# 2. Start Spring Boot in the background, wait until it answers, then run the scraper
#    every SCRAPE_INTERVAL seconds while the backend is alive (Ctrl+C stops everything).
#    The time of the last scrape is kept in SCRAPE_STAMP, so restarts don't re-scrape.
backend:
	@if fuser -s $(PORT)/tcp 2>/dev/null || curl -s -o /dev/null $(HEALTH_URL); then \
		echo "Port $(PORT) is already in use, stop the other backend first"; exit 1; \
	fi
	@(cd $(BACKEND_DIR) && exec ./mvnw spring-boot:run) > $(BACKEND_LOG) 2>&1 & \
	PID=$$!; \
	SCRAPING=0; \
	cleanup() { \
		kill $$PID 2>/dev/null; \
		fuser -k -s $(PORT)/tcp 2>/dev/null; \
		if [ "$$SCRAPING" = 1 ]; then \
			rm -f $(SCRAPE_STAMP); \
			echo "Scrape interrupted, it will run again on the next start"; \
		fi; \
	}; \
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
	echo "Backend is up."; \
	if [ ! -x $(SCRAPER_DIR)/$(PYTHON) ]; then \
		echo "No Python venv in $(SCRAPER_DIR)/venv, run 'make setup' first"; exit 1; \
	fi; \
	while kill -0 $$PID 2>/dev/null; do \
		last=$$(stat -c %Y $(SCRAPE_STAMP) 2>/dev/null || echo 0); \
		if [ $$(( $$(date +%s) - $$last )) -ge $(SCRAPE_INTERVAL) ]; then \
			echo "[$$(date)] Running the scraper..."; \
			touch $(SCRAPE_STAMP); \
			SCRAPING=1; \
			(cd $(SCRAPER_DIR) && xvfb-run -a timeout --foreground -k 60 $(SCRAPE_TIMEOUT) $(PYTHON) Scraper_Manager.py); \
			rc=$$?; \
			SCRAPING=0; \
			if [ $$rc -eq 124 ]; then \
				echo "[$$(date)] Scraper timed out after $(SCRAPE_TIMEOUT), next attempt in $(SCRAPE_INTERVAL)s"; \
			elif [ $$rc -ne 0 ]; then \
				echo "[$$(date)] Scraper failed (exit $$rc), next attempt in $(SCRAPE_INTERVAL)s"; \
			fi; \
		fi; \
		sleep 600; \
	done; \
	echo "Backend stopped, last lines of $(BACKEND_LOG):"; tail -n 30 $(BACKEND_LOG); exit 1

# 2b. Start Spring Boot in the foreground without running the scraper (Ctrl+C stops it)
backend_noscrape:
	cd $(BACKEND_DIR) && ./mvnw spring-boot:run

# 3. Create the scraper's Python venv and install its dependencies
setup:
	cd $(SCRAPER_DIR) && python3 -m venv venv && venv/bin/pip install -r requirements.txt

# 4. Remove every compiled Java class file
clean:
	find . -name "*.class" -not -path "*/node_modules/*" -delete

# 5. Delete every local H2 database file and the scrape stamp (refuses while the backend is running)
clean_database:
	@if curl -s -o /dev/null $(HEALTH_URL); then \
		echo "Backend is running on port $(PORT), stop it before cleaning the database"; exit 1; \
	fi
	find . \( -name "*.mv.db" -o -name "*.trace.db" -o -name "*.lock.db" \) \
		-not -path "*/node_modules/*" -not -path "*/venv/*" -print -delete
	rm -f $(SCRAPE_STAMP)