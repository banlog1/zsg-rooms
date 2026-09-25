#define main zsg_search_main
#include "seed_finder.c"
#undef main
#include <assert.h>

int main(void) {
    assert(!portal_has_tool((PortalLoot){.steel=1,.charges=5}));
    assert(portal_has_tool((PortalLoot){.axes=1}));
    assert(portal_has_tool((PortalLoot){.pickaxes=1}));
    assert(portal_has_tool((PortalLoot){.axes=1,.pickaxes=1}));
    Result result={0};
    assert(parse_portal("PORTAL_RESULT 4 2 0 0 1 65\n",&result));
    assert(parse_portal("PORTAL_RESULT 3 5 12 3 2 65\n",&result));
    assert(parse_portal("PORTAL_RESULT 0 0 0 0 0 0\n",&result));
    assert(!parse_portal("PORTAL_RESULT 3 5 2 3 2 65\n",&result));
    assert(!parse_portal("PORTAL_RESULT 4 2 0 1 1 65\n",&result));
    assert(!parse_portal("PORTAL_RESULT 4 2 0 0 1 65 extra\n",&result));
    assert(!parse_portal("PORTAL_RESULT 4 2 0 0 1\n",&result));
    assert(!parse_portal("PORTAL_RESULT 5 2 0 0 1 65\n",&result));
    assert(portal_resources((PortalLoot){.obsidian=2,.steel=1}));
    assert(!portal_resources((PortalLoot){.obsidian=10,.nuggets=8,.flint=1}));
    for(int charges=0;charges<5;charges++) {
        assert(!portal_resources((PortalLoot){.obsidian=10,.charges=charges}));
        assert(!portal_resources((PortalLoot){.charges=charges,.nuggets=8,.flint=1}));
        assert(!portal_resources((PortalLoot){.charges=charges,.nuggets=9}));
        assert(portal_resources((PortalLoot){.charges=charges,.steel=1}));
        assert(portal_resources((PortalLoot){.charges=charges,.nuggets=9,.flint=1}));
    }
    assert(portal_resources((PortalLoot){.charges=5}));
    assert(portal_resources((PortalLoot){.charges=6}));
    for(int i=0;i<4096;i++) {
        uint64_t seed=(uint64_t)i*UINT64_C(0x9e3779b97f4a7c15);
        Pos pos={(i%31-15)*16,(i%23-11)*16};
        PortalLoot loot=portal_loot(seed,pos);
        printf("%d %d %d %d %d %d %d %d\n",i,loot.obsidian,loot.nuggets,loot.flint,loot.steel,loot.charges,loot.axes,loot.pickaxes);
    }
    return 0;
}
