#ifndef ZSG_SEARCH_POLICY_H
#define ZSG_SEARCH_POLICY_H

enum { POLICY_BUDGETS=6, POLICY_CAPS=3, POLICY_COUNT=POLICY_BUDGETS*POLICY_CAPS };
static const unsigned policy_budgets[POLICY_BUDGETS]={64,256,1024,4096,16384,65536};
static const unsigned policy_caps[POLICY_CAPS]={1,2,4};

typedef struct { unsigned accepted; double ms; int settled; } PolicyObservation;
typedef struct { uint64_t accepted, productive; double ms; } PolicyTotal;

/* Call at acceptances and power-of-two attempt counts. No seed identities are stored. */
static void policy_observe(PolicyObservation *observations, unsigned tried, unsigned accepted, double ms) {
    for (int b=0;b<POLICY_BUDGETS;b++) for (int c=0;c<POLICY_CAPS;c++) {
        PolicyObservation *p=&observations[b*POLICY_CAPS+c];
        if (!p->settled && (tried>=policy_budgets[b] || accepted>=policy_caps[c]))
            *p=(PolicyObservation){accepted,ms,1};
    }
}

/* An interrupted family is excluded for every policy, avoiding partial-sample comparisons. */
static int policy_commit(PolicyTotal *totals, const PolicyObservation *observations) {
    for (int i=0;i<POLICY_COUNT;i++) if (!observations[i].settled) return 0;
    for (int i=0;i<POLICY_COUNT;i++) {
        totals[i].accepted+=observations[i].accepted;
        totals[i].productive+=observations[i].accepted!=0;
        totals[i].ms+=observations[i].ms;
    }
    return 1;
}

#endif
