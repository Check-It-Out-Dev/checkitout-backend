package com.sm.instagram.platform.activecooperations;

/**
 * Jackson view marker interfaces used to project active-cooperation DTOs at
 * different verbosity levels. Each view widens the previous one:
 *
 *   Basic
 *     -> Ratings (adds rating aggregates)
 *     -> Registration (adds registration-time fields)
 *
 * The InProgress_* family fans out into role-specific projections:
 *
 *   InProgress_InfluencerView (influencer-facing fields)
 *   InProgress_CompanyView    (company-facing fields)
 *   InProgress_AdminView      (union of both, used by admin tooling)
 *
 * Marker classes are intentionally empty: behaviour comes from
 * {@code @JsonView} annotations on the DTOs themselves.
 */
public final class Views {

    private Views() {
        // utility holder, never instantiated
    }

    public interface Basic {
    }

    public interface Ratings extends Basic {
    }

    public interface Registration extends Ratings {
    }

    public interface InProgress_InfluencerView extends Basic {
    }

    public interface InProgress_CompanyView extends Basic {
    }

    public interface InProgress_AdminView
            extends InProgress_InfluencerView, InProgress_CompanyView {
    }
}
