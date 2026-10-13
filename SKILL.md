# ZapClone Project Context

## Services
- zap-service: port 8081, package org.zapier
- trigger-service: port 8082
- execution-service: port 8083, package org.zapier.executionservice
- action-service: port 8084, package com.zapier.actionservice
- api-gateway: port 8080
- eureka-server: port 8761

## Tech
- Spring Boot 3.2.5
- PostgreSQL
- Kafka
- application.properties not yml
- @Getter @Setter not @Data
- ApiResponse<T> wrapper for all responses

## Current auth
- No auth yet
- userId hardcoded as "user-123" everywhere
- Need to replace with real JWT auth

## Database
- zapdb for zap-service
- All on localhost:5432

## Google Calendar Integration (to be built)
- OAuth2 tokens stored in auth-service database
- Calendar polling trigger in trigger-service
- Calendar create event action in action-service
- Uses Google Calendar API v3
- Scopes needed: calendar.readonly, calendar.events
