# CheckItOut Platform - System Documentation

## 🎯 Executive Summary

**CheckItOut** is an **Instagram-integrated partnership and collaboration platform** that connects influencers, content creators, and businesses for sponsorship opportunities and collaborative ventures. Built on Spring Boot with a modular architecture, it provides a complete ecosystem for managing partnership lifecycles from discovery to active cooperation.

## 📊 System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     CheckItOut Platform v2.0                    │
├─────────────────────────────────────────────────────────────────┤
│  Package: com.sm.instagram.platform                             │
│  Tech Stack: Spring Boot 3.x, PostgreSQL, Redis, Firebase       │
│  Architecture: Modular Monolith (Microservice-Ready)            │
│  Integration: Instagram API, Firebase Auth                      │
└─────────────────────────────────────────────────────────────────┘
```

## 🏛️ System Architecture

### High-Level Architecture (ArchiMate Style)

```mermaid
graph TB
    subgraph "Business Layer"
        BF1[Partnership Discovery]
        BF2[Application Management]
        BF3[Cooperation Tracking]
        BF4[User Engagement]
    end
    
    subgraph "Application Layer"
        AS1[Auth Service]
        AS2[Opportunity Service]
        AS3[Cooperation Service]
        AS4[User Service]
        AS5[Social Integration]
    end
    
    subgraph "Technology Layer"
        subgraph "API Gateway"
            GW[Spring REST Controllers<br/>40+ endpoints]
        end
        
        subgraph "Core Services"
            CS1[Business Logic]
            CS2[Security Layer]
            CS3[Rate Limiting]
            CS4[Exception Handling]
        end
        
        subgraph "Infrastructure"
            DB[(PostgreSQL)]
            CACHE[(Redis)]
            FB[Firebase]
            IG[Instagram API]
        end
    end
    
    BF1 --> AS2
    BF2 --> AS2
    BF3 --> AS3
    BF4 --> AS4
    
    AS1 --> CS2
    AS2 --> CS1
    AS3 --> CS1
    AS4 --> CS1
    AS5 --> IG
    
    CS1 --> DB
    CS2 --> FB
    CS3 --> CACHE
```

## 🔧 Subsystem Architecture

### Core Subsystems

```
┌──────────────────────────────────────────────────────────────┐
│                        SUBSYSTEMS                            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────┐        │
│  │ BusinessLogic (PageRank: 0.3)                    │        │
│  │ • Opportunities Management                       │        │
│  │ • Cooperations Tracking                          │        │
│  │ • User Management                                │        │
│  │ • Admin Operations                               │        │
│  │ • Social Sync                                    │        │
│  └──────────────────────────────────────────────────┘        │
│                           │                                  │
│                    DEPENDS ON                                │
│                ┌──────────┴───────────┐                      │
│                ▼                      ▼                      │
│  ┌─────────────────────┐   ┌─────────────────────┐           │
│  │ Security (PR: 0.2)  │   │ RateLimiting (0.1)  │           │
│  │ • JWT Auth          │   │ • Redis-based       │           │
│  │ • Firebase Auth     │   │ • Throttling        │           │
│  │ • 2FA/TOTP          │   │ • API Limits        │           │
│  │ • Session Mgmt      │   │                     │           │
│  └─────────────────────┘   └─────────────────────┘           │
│                                                              │
│  ┌─────────────────────────────────────────────────┐         │
│  │ Infrastructure (PageRank: 0.3)                  │         │
│  │ • Docker Deployment                             │         │
│  │ • CI/CD Pipeline                                │         │
│  │ • Monitoring (Loki/Grafana)                     │         │
│  └─────────────────────────────────────────────────┘         │
│                                                              │
│  ┌─────────────────────────────────────────────────┐         │
│  │ ExceptionHandling (PageRank: 0.1)               │         │
│  │ • Global Exception Handler                      │         │
│  │ • Business Exception Management                 │         │
│  └─────────────────────────────────────────────────┘         │
└──────────────────────────────────────────────────────────────┘
```

## 📦 Module Structure

### Package Organization

```
com.sm.instagram.platform/
├── auth/                        # Authentication & Authorization
│   ├── controller/              # Auth endpoints
│   ├── service/                 # Auth business logic
│   ├── cache/                   # User caching (Redis/InMemory)
│   └── firebase/                # Firebase integration
│
├── appliedopportunities/        # Core Business Domain
│   ├── controller/              # REST endpoints
│   ├── service/                 # Business logic
│   ├── repository/              # Data access
│   ├── dto/                     # Data transfer objects
│   └── domain/                  # Domain entities
│
├── activecooperations/          # Active Partnership Management
│   ├── controller/
│   └── service/
│
├── address/                     # Location Services
│   ├── controller/
│   ├── service/
│   └── geo/                     # GeoIP services
│
├── security/                    # Security Infrastructure
│   ├── jwt/                     # JWT handling
│   ├── ratelimit/              # Rate limiting
│   └── recaptcha/              # Bot protection
│
├── common/                      # Shared Components
│   ├── exception/              # Exception handling
│   ├── validation/             # Input validation
│   └── util/                   # Utilities
│
└── config/                      # Configuration
    ├── redis/                   # Redis config
    ├── firebase/               # Firebase config
    └── security/               # Security config
```

## 🔄 Core Business Flows

### 1. Partnership Opportunity Discovery & Application

```mermaid
sequenceDiagram
    actor User
    participant Mobile as Mobile App
    participant Auth as Auth Service
    participant OppCtrl as Opportunity Controller
    participant OppSvc as Opportunity Service
    participant Cache as Redis Cache
    participant DB as PostgreSQL
    participant Status as Status History
    
    User->>Mobile: Open App
    Mobile->>Auth: Authenticate (Instagram/Firebase)
    Auth->>Auth: Validate JWT Token
    Auth->>Cache: Check User Cache
    Auth-->>Mobile: Auth Token
    
    Mobile->>OppCtrl: GET /opportunities
    OppCtrl->>OppSvc: getAvailableOpportunities()
    OppSvc->>Cache: Check Opportunity Cache
    
    alt Cache Miss
        OppSvc->>DB: Query Opportunities
        DB-->>OppSvc: Opportunity List
        OppSvc->>Cache: Update Cache
    end
    
    OppSvc-->>OppCtrl: OpportunityDtoOut[]
    OppCtrl-->>Mobile: Opportunity List
    Mobile-->>User: Display Opportunities
    
    User->>Mobile: Select & Apply
    Mobile->>OppCtrl: POST /applied-opportunities
    OppCtrl->>OppSvc: applyForOpportunity(dto)
    
    OppSvc->>DB: Save Application
    OppSvc->>Status: Log Status Change
    OppSvc->>Cache: Invalidate User Stats
    
    OppSvc-->>OppCtrl: ApplicationResult
    OppCtrl-->>Mobile: Confirmation
    Mobile-->>User: Application Submitted
```

### 2. Authentication Flow with Instagram Integration

```mermaid
sequenceDiagram
    actor User
    participant App
    participant FirebaseAuth
    participant InstagramAPI
    participant TokenService
    participant UserCache
    participant DB
    
    User->>App: Login with Instagram
    App->>InstagramAPI: OAuth Request
    InstagramAPI-->>User: Instagram Login Page
    User->>InstagramAPI: Credentials
    InstagramAPI-->>App: OAuth Token
    
    App->>FirebaseAuth: Exchange for Firebase Token
    FirebaseAuth->>TokenService: Generate JWT
    TokenService->>TokenService: Sign with KMS Key
    TokenService-->>FirebaseAuth: JWT Token
    
    FirebaseAuth->>UserCache: Cache User Session
    FirebaseAuth->>DB: Update User Record
    FirebaseAuth-->>App: Auth Response
    App-->>User: Logged In
```

### 3. Active Cooperation Lifecycle

```
┌──────────────────────────────────────────────────────────────┐
│                  COOPERATION LIFECYCLE                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  [DISCOVERED]                                                │
│       │                                                      │
│       ▼                                                      │
│  [APPLIED] ──────► [REJECTED]                                │
│       │                                                      │
│       ▼                                                      │
│  [UNDER_REVIEW]                                              │
│       │                                                      │
│       ▼                                                      │
│  [NEGOTIATION]                                               │
│       │                                                      │
│       ▼                                                      │
│  [APPROVED]                                                  │
│       │                                                      │
│       ▼                                                      │
│  [ACTIVE_COOPERATION] ◄──────┐                               │
│       │                      │                               │
│       ├──► [ON_HOLD] ────────┘                               │
│       │                                                      │
│       ▼                                                      │
│  [COMPLETED]                                                 │
│       │                                                      │
│       ▼                                                      │
│  [ARCHIVED]                                                  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

## 🏗️ Layered Architecture

### Layer Dependencies

```mermaid
graph TD
    subgraph "Presentation Layer"
        CTRL[Controllers<br/>40+ REST endpoints]
    end
    
    subgraph "Application Layer"
        SVC[Services<br/>80+ business services]
        MAPPER[DTO Mappers]
    end
    
    subgraph "Domain Layer"
        ENTITY[Entities<br/>35+ JPA entities]
        SPEC[Specifications]
        ENUM[Enums & Constants]
    end
    
    subgraph "Infrastructure Layer"
        REPO[Repositories<br/>40+ data repositories]
        CACHE[Cache Layer<br/>Redis/InMemory]
        CONFIG[Configuration]
    end
    
    subgraph "Cross-Cutting Concerns"
        SEC[Security]
        LOG[Logging]
        EXC[Exception Handling]
        RATE[Rate Limiting]
    end
    
    CTRL --> SVC
    CTRL --> MAPPER
    SVC --> ENTITY
    SVC --> SPEC
    SVC --> REPO
    REPO --> ENTITY
    CACHE --> REPO
    
    SEC -.-> CTRL
    SEC -.-> SVC
    LOG -.-> SVC
    EXC -.-> CTRL
    RATE -.-> CTRL
```

## 📊 Data Model Overview

### Core Entities

```
┌─────────────────────────────────────────────────────┐
│                    USER                             │
├─────────────────────────────────────────────────────┤
│ • id: UUID                                          │
│ • instagramId: String                               │
│ • email: String                                     │
│ • userType: INFLUENCER|BUSINESS|ADMIN               │
│ • preferences: UserPreferences                      │
│ • socialConnections: List<UserSocialConnection>     │
└─────────────────────────────────────────────────────┘
           │                            │
           │                            │
           ▼                            ▼
┌─────────────────────┐    ┌────────────────────────────┐
│ PARTNERSHIP         │    │ APPLIED_OPPORTUNITY        │
│ OPPORTUNITY         │    ├──────────────────────────  ┤
├─────────────────────┤    │ • user: User               │
│ • title             │◄───│ • opportunity: Partnership │
│ • description       │    │ • status: OpportunityStatus│
│ • requirements      │    │ • appliedDate: DateTime    │
│ • compensation      │    │ • content: List<Content>   │
│ • photos: List      │    │ • statusHistory: List      │
└─────────────────────┘    └────────────────────────────┘
                                       │
                                       ▼
                            ┌──────────────────────────┐
                            │ ACTIVE_COOPERATION       │
                            ├──────────────────────────┤
                            │ • startDate: DateTime    │
                            │ • endDate: DateTime      │
                            │ • deliverables: List     │
                            │ • performance: Metrics   │
                            └──────────────────────────┘
```

## 🔐 Security Architecture

### Security Layers

```
┌──────────────────────────────────────────────────────────┐
│                    REQUEST FLOW                          │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  [Client Request]                                        │
│        │                                                 │
│        ▼                                                 │
│  [Rate Limiter] ◄─── Redis                               │
│        │                                                 │
│        ▼                                                 │
│  [JWT Filter] ◄─── Token Validation                      │
│        │                                                 │
│        ▼                                                 │
│  [Authentication] ◄─── Firebase/Instagram                │
│        │                                                 │
│        ▼                                                 │
│  [Authorization] ◄─── Role-Based Access                  │
│        │                                                 │
│        ▼                                                 │
│  [2FA Check] ◄─── TOTP/QR Code (if enabled)              │
│        │                                                 │
│        ▼                                                 │
│  [GeoIP Validation] ◄─── Location Verification           │
│        │                                                 │
│        ▼                                                 │
│  [Business Logic]                                        │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

## 🚀 Deployment Architecture

### Infrastructure Overview

```mermaid
graph LR
    subgraph "Container Infrastructure"
        subgraph "Application Containers"
            APP1[App Instance 1]
            APP2[App Instance 2]
            APP3[App Instance 3]
        end
        
        subgraph "Data Layer"
            PG[(PostgreSQL<br/>Primary)]
            PG_R[(PostgreSQL<br/>Read Replica)]
            REDIS[(Redis Cluster)]
        end
        
        subgraph "Monitoring Stack"
            LOKI[Loki]
            GRAF[Grafana]
            ALLOY[Alloy Agent]
        end
    end
    
    subgraph "External Services"
        FB[Firebase]
        IG[Instagram API]
        CDN[CDN/Storage]
    end
    
    LB[Load Balancer] --> APP1
    LB --> APP2
    LB --> APP3
    
    APP1 --> PG
    APP2 --> PG_R
    APP3 --> REDIS
    
    APP1 --> FB
    APP2 --> IG
    APP3 --> CDN
    
    APP1 --> ALLOY
    ALLOY --> LOKI
    LOKI --> GRAF
```

## 📈 Performance Metrics

### System Characteristics

```
┌──────────────────────────────────────────────────────┐
│              PERFORMANCE PROFILE                     │
├──────────────────────────────────────────────────────┤
│                                                      │
│  Total Files:           426 Java files               │
│  Controllers:           40+ REST endpoints           │
│  Services:              80+ business services        │
│  Repositories:          40+ data repositories        │
│  DTOs:                 30+ transfer objects          │
│  Entities:              35+ JPA entities             │
│                                                      │
│  Architecture Layers:   5 (clearly separated)        │
│  Subsystems:           5 major subsystems            │
│  Graph Density:        0.667 (well-connected)        │
│  Coupling:             Medium (refactoring needed)   │
│                                                      │
│  Cache Strategy:       Redis + In-Memory fallback    │
│  Auth Methods:         JWT + Firebase + Instagram    │
│  Rate Limiting:        Redis-based per-user/IP       │
│                                                      │
└──────────────────────────────────────────────────────┘
```

## 🔄 Integration Points

### External System Integration

```
┌─────────────────────────────────────────────────────────┐
│                  INTEGRATION MAP                        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Instagram API                                          │
│  ├── OAuth Authentication                               │
│  ├── User Profile Sync                                  │
│  ├── Follower Count Updates                             │
│  └── Content Verification                               │
│                                                         │
│  Firebase Services                                      │
│  ├── Authentication                                     │
│  ├── Cloud Firestore (User Documents)                   │
│  ├── Cloud Storage (Media)                              │
│  └── Push Notifications                                 │
│                                                         │
│  Redis Cache                                            │
│  ├── User Session Cache                                 │
│  ├── Rate Limiting Counters                             │
│  ├── Opportunity Cache                                  │
│  └── GeoLocation Cache                                  │
│                                                         │
│  PostgreSQL Database                                    │
│  ├── User Management                                    │
│  ├── Opportunity Storage                                │
│  ├── Application Tracking                               │
│  └── Audit Logs                                         │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## 🛠️ API Endpoints Overview

### Main Controller Groups

| Controller | Purpose | Key Endpoints |
|------------|---------|---------------|
| **AuthController** | Authentication | POST /auth/login, /auth/refresh, /auth/logout |
| **PartnershipOpportunityController** | Browse opportunities | GET /opportunities, GET /opportunities/{id} |
| **AppliedOpportunityController** | Manage applications | POST /applied-opportunities, GET /my-applications |
| **ActiveCooperationController** | Active partnerships | GET /cooperations, PUT /cooperations/{id}/status |
| **UserController** | User management | GET /users/profile, PUT /users/preferences |
| **UserSocialConnectionController** | Social integration | POST /social/connect, GET /social/stats |
| **AdminController** | Administration | GET /admin/users, POST /admin/approve |

## 🔍 Key Technical Insights

### Discovered Issues & Recommendations

1. **Circular Dependencies in Auth Module**
   - Problem: Auth components have circular references
   - Impact: Tight coupling, difficult testing
   - Solution: Extract AuthenticationFacade interface

2. **High Coupling in FirebaseAuthProxy**
   - Problem: 8 dependencies (should be 4-5 max)
   - Impact: Brittleness, hard to maintain
   - Solution: Apply Dependency Inversion Principle

3. **Missing Service Layer Abstraction**
   - Problem: Some controllers directly access repositories
   - Impact: Business logic leakage
   - Solution: Enforce service layer for all operations

4. **Redis Fallback Strategy**
   - Strength: InMemory cache fallback when Redis unavailable
   - Ensures system resilience

## 📝 Development Guidelines

### Code Organization Standards

```java
// Standard Service Pattern
@Service
@RequiredArgsConstructor
public class OpportunityService {
    private final OpportunityRepository repository;
    private final UserCacheService cacheService;
    private final OpportunityMapper mapper;
    
    @Transactional
    public OpportunityDtoOut applyForOpportunity(OpportunityDtoIn dto) {
        // 1. Validation
        validateApplication(dto);
        
        // 2. Business Logic
        var entity = mapper.toEntity(dto);
        entity.setStatus(OpportunityStatus.APPLIED);
        
        // 3. Persistence
        var saved = repository.save(entity);
        
        // 4. Cache Invalidation
        cacheService.invalidateUserStats(dto.getUserId());
        
        // 5. Return DTO
        return mapper.toDto(saved);
    }
}
```

## 🚦 Getting Started

### Prerequisites
- Java 21
- PostgreSQL 16+
- Redis 6+
- Firebase Project
- Instagram App Registration

### Environment Variables
```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=checkitout

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Firebase
FIREBASE_PROJECT_ID=your-project
FIREBASE_PRIVATE_KEY=your-key

# Instagram
INSTAGRAM_APP_ID=your-app-id
INSTAGRAM_APP_SECRET=your-secret
```

### Build & Run
```bash
# Build
./mvnw clean package

# Run with profile
./mvnw spring-boot:run -Dspring.profiles.active=dev

# Docker deployment
docker-compose up -d
```

## 📚 Additional Resources

- API Documentation: `/swagger-ui.html`
- Monitoring Dashboard: `http://localhost:3000` (Grafana)
- Health Check: `/actuator/health`
- Metrics: `/actuator/metrics`

---

*Document Version: 2.0.0 | Generated from System Analysis | CheckItOut Platform*