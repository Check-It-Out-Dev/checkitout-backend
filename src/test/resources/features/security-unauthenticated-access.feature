@security-suite @401-unauthenticated
Feature: Unauthenticated Access Protection
  As the platform security system
  I must reject all unauthenticated requests to protected resources
  So that user data and business operations remain secure

  Background:
    Given the application is running with real Redis

  # =============================================================================
  # USER MANAGEMENT ENDPOINTS
  # =============================================================================
  # All user-related endpoints require authentication

  @user-endpoints
  Scenario Outline: User endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | GET    | /users/me                             |
      | GET    | /users/1                              |
      | GET    | /users/paged                          |
      | GET    | /users/user/test-firebase-uid         |
      | GET    | /users/accounts/status                |
      | GET    | /users/type                           |
      | GET    | /users/me/deletion-eligibility        |
      | GET    | /users/1/deletion-eligibility         |
      | GET    | /users/influencers/1/public-profile   |
      | GET    | /users/companies/1/public-profile     |
      | GET    | /users/paged/public-profile           |
      | POST   | /users                                |
      | PUT    | /users/1                              |
      | PATCH  | /users/1                              |
      | PATCH  | /users/1/premium                      |
      | DELETE | /users/1                              |
      | DELETE | /users/delete-permanently/1           |

  # =============================================================================
  # USER PREFERENCES ENDPOINTS
  # =============================================================================

  @user-preferences-endpoints
  Scenario Outline: User preferences endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | GET    | /user-preferences/me                  |
      | PUT    | /user-preferences/me                  |
      | PATCH  | /user-preferences/me                  |
      | GET    | /user-preferences/user/1              |
      | PATCH  | /user-preferences/user/1              |

  # =============================================================================
  # PARTNERSHIP OPPORTUNITY ENDPOINTS
  # =============================================================================

  @partnership-endpoints
  Scenario Outline: Partnership opportunity endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | GET    | /partnership-opportunity/1            |
      | GET    | /partnership-opportunity/paged        |
      | GET    | /partnership-opportunity/compensation/type |
      | POST   | /partnership-opportunity              |
      | PUT    | /partnership-opportunity/1            |
      | PATCH  | /partnership-opportunity/1            |

  # =============================================================================
  # APPLIED OPPORTUNITY ENDPOINTS
  # =============================================================================

  @applied-opportunity-endpoints
  Scenario Outline: Applied opportunity endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                                           |
      | GET    | /applied-opportunity/1                             |
      | GET    | /applied-opportunity/paged                         |
      | GET    | /applied-opportunity/statistics                    |
      | GET    | /applied-opportunity/1/payment-contact             |
      | GET    | /applied-opportunity/1/status-history              |
      | GET    | /applied-opportunity/1/status-history/paged        |
      | POST   | /applied-opportunity                               |
      | PUT    | /applied-opportunity/1/company-rating              |
      | PUT    | /applied-opportunity/1/influencer-rating           |
      | PATCH  | /applied-opportunity/rate/update/1                 |
      | PATCH  | /applied-opportunity/status/update/1               |

  # =============================================================================
  # APPLIED OPPORTUNITY CONTENT ENDPOINTS
  # =============================================================================

  @content-endpoints
  Scenario Outline: Applied opportunity content endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                                              |
      | GET    | /applied-opportunity/content/1                        |
      | GET    | /applied-opportunity/content/applied-opportunity/1    |
      | GET    | /applied-opportunity/content/pending-approval         |
      | POST   | /applied-opportunity/content                          |
      | PUT    | /applied-opportunity/content/1                        |
      | PATCH  | /applied-opportunity/content/1/approve                |
      | PATCH  | /applied-opportunity/content/1/reject                 |
      | DELETE | /applied-opportunity/content/1                        |

  # =============================================================================
  # FILE UPLOAD/STORAGE ENDPOINTS
  # =============================================================================

  @upload-endpoints
  Scenario Outline: File upload endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | POST   | /upload/signed-url                    |
      | POST   | /upload/confirm/test-upload-id        |
      | GET    | /upload/limits                        |
      | GET    | /upload/health                        |
      | GET    | /upload/stats                         |
      | DELETE | /files/delete                         |
      | DELETE | /files/batch-delete                   |
      | DELETE | /files/folder                         |

  # =============================================================================
  # ADMIN ENDPOINTS
  # =============================================================================

  @admin-endpoints
  Scenario Outline: Admin endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | POST   | /admin/user-claims/test-uid           |
      | GET    | /admin/geoip/metrics                  |
      | GET    | /admin/geoip/lookup/8.8.8.8           |
      | GET    | /admin/geoip/my-location              |
      | POST   | /admin/geoip/update-database          |
      | POST   | /admin/geoip/clean-cache              |
      | GET    | /admin/uploads/stats/system           |
      | GET    | /admin/uploads/user/1                 |
      | POST   | /admin/uploads/cleanup/orphaned       |
      | GET    | /admin/consent/definitions            |
      | POST   | /admin/consent/definitions            |
      | GET    | /admin/consent/users/1                |

  # =============================================================================
  # CONSENT ENDPOINTS
  # =============================================================================

  @consent-endpoints
  Scenario Outline: Consent endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | GET    | /consent/my                           |
      | POST   | /consent/my                           |
      | GET    | /consent/my/history/TERMS_OF_SERVICE  |

  # =============================================================================
  # ADDRESS ENDPOINTS
  # =============================================================================

  @address-endpoints
  Scenario Outline: Address endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | GET    | /address/1                            |
      | GET    | /address/types                        |
      | GET    | /address/user/1                       |
      | GET    | /address/user/1/primary               |
      | GET    | /address/search                       |
      | POST   | /address                              |
      | POST   | /address/user/1                       |
      | POST   | /address/1/primary                    |
      | PUT    | /address/1                            |
      | DELETE | /address/1                            |

  # =============================================================================
  # 2FA ENDPOINTS
  # =============================================================================

  @2fa-endpoints
  Scenario Outline: Two-factor authentication endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | GET    | /twofactor/status                     |
      | POST   | /twofactor/setup                      |
      | POST   | /twofactor/verify-setup               |
      | POST   | /twofactor/verify                     |
      | POST   | /twofactor/disable                    |
      | POST   | /twofactor/backup-codes               |

  # =============================================================================
  # AUTH REFRESH SESSION
  # =============================================================================

  @auth-refresh
  Scenario: Refresh session endpoint requires existing session
    When I make an unauthenticated POST request to "/auth/refresh-session"
    Then the response status should be 401

  # =============================================================================
  # USER SOCIAL CONNECTION ENDPOINTS
  # =============================================================================

  @social-connection-endpoints
  Scenario Outline: User social connection endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | POST   | /user-social-connection               |
      | GET    | /user-social-connection/1             |
      | GET    | /user-social-connection/paged         |
      | PUT    | /user-social-connection/1             |
      | PATCH  | /user-social-connection/1             |
      | DELETE | /user-social-connection/1             |

  # =============================================================================
  # CITY/REFERENCE DATA ENDPOINTS (Admin Write Operations)
  # =============================================================================

  @reference-data-admin
  Scenario Outline: Reference data admin endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | POST   | /city                                 |
      | PUT    | /city/1                               |
      | PATCH  | /city/1                               |
      | DELETE | /city/1                               |

  # =============================================================================
  # ACTIVE COOPERATION ENDPOINTS
  # =============================================================================

  @active-cooperation-endpoints
  Scenario Outline: Active cooperation endpoints require authentication
    When I make an unauthenticated <method> request to "<endpoint>"
    Then the response status should be 401

    Examples:
      | method | endpoint                              |
      | GET    | /activecoop/rate                      |
      | GET    | /activecoop/accept                    |
      | GET    | /activecoop/inprogress                |
      | PUT    | /activecoop/1/company-rating          |
      | PUT    | /activecoop/1/influencer-rating       |
