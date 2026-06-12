# Logging Module

This module provides comprehensive request/response logging with trace ID support for the CheckItOut Backend application.

## Features

- **Request Correlation**: Unique trace IDs for tracking requests across the system
- **Request/Response Logging**: Detailed logging of HTTP requests and responses
- **CORS Logging**: Debug logging for CORS-related issues
- **Request Body Caching**: Allows multiple reads of request body for logging
- **Structured Logging**: JSON-formatted logs for better parsing and analysis

## Components

- **RequestCorrelationFilter**: Adds unique trace IDs to each request
- **RequestLoggingFilter**: Logs detailed request/response information
- **RequestBodyCachingFilter**: Enables request body to be read multiple times
- **CorsLoggingFilter**: Logs CORS preflight and actual requests
- **EarlyRequestLoggingFilter**: Captures requests early in the filter chain

## Configuration

Logging is configured via `logback-spring.xml` with different profiles:
- Development: Console output with color coding
- Test/Production: JSON-formatted logs for log aggregation systems

## Usage

All requests automatically get a trace ID that appears in logs:
```
[TRACE-ID: 550e8400-e29b-41d4-a716-446655440000] Processing request...
```

This trace ID is also returned in response headers as `X-Trace-Id` for client-side correlation.
