COMPOSE ?= docker compose
COMPOSE_DEV ?= $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml

.PHONY: help up up-d up-dev build down clean logs ps infra

help: ## Show this help
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-8s %s\n", $$1, $$2}'

up: ## Start all services in the foreground (app, Postgres, MinIO, Mailpit, web)
	$(COMPOSE) up

up-d: ## Start all services in the background
	$(COMPOSE) up -d

up-dev: ## Build and start all services over plain HTTP (cookies not Secure); the e2e stack
	$(COMPOSE_DEV) up -d --build --wait

build: ## Rebuild images, then start all services in the background
	$(COMPOSE) up -d --build

infra: ## Start only Postgres, MinIO and Mailpit
	$(COMPOSE) up -d postgres minio mailpit

down: ## Stop and remove containers (keeps data volumes)
	$(COMPOSE) down

clean: ## Stop and remove containers and data volumes
	$(COMPOSE) down -v

logs: ## Follow service logs
	$(COMPOSE) logs -f

status: ## Show service status
	$(COMPOSE) ps
