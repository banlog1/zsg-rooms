#include <assert.h>
#define main(...) unused_finder_main(__VA_ARGS__)
#include "seed_finder.c"
#undef main

int main(void) {
    batch_timing=1;
    deadline_refresh=0;
    assert(!family_within_deadline(0,-1));
    for(uint64_t i=1;i<TIMING_INTERVAL;i++) assert(family_within_deadline(i,-1));
    assert(!family_within_deadline(TIMING_INTERVAL,-1));
    deadline_refresh=1;
    assert(!family_within_deadline(1,-1));
    assert(!deadline_refresh);
    assert(family_within_deadline(2,now_ms()+1000));
    batch_timing=0;
    assert(!family_within_deadline(1,-1));
    assert(family_within_deadline(1,now_ms()+1000));
    memset(reached,0,sizeof(reached));
    memset(failed,0,sizeof(failed));
    memset(stage_ms,0,sizeof(stage_ms));
    geometry_samples=0;
    assert(!done(GEOMETRY,-1,0));
    assert(reached[GEOMETRY]==1 && failed[GEOMETRY]==1 && geometry_samples==0 && stage_ms[GEOMETRY]==0);
    assert(done(GEOMETRY,now_ms(),1));
    assert(reached[GEOMETRY]==2 && failed[GEOMETRY]==1 && geometry_samples==1);
    puts("PASS: bounded cheap-family polling, refresh after expensive work, exact mode, sampled counts");
    return 0;
}
