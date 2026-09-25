#define main finder_main
#include "seed_finder.c"
#undef main
#include <assert.h>

static int check_bank(void) {
    Generator g;
    setupGenerator(&g,MC_1_16_1,0);
    char line[512], extra;
    unsigned checked=0;
    while(fgets(line,sizeof(line),stdin)) {
        int64_t seed;
        Pos main,entry,wood,spawn,bastion,fortress;
        if(sscanf(line,"%" SCNd64 " %d %d %d %d %d %d %d %d %d %d %d %d %c",&seed,
            &main.x,&main.z,&entry.x,&entry.z,&wood.x,&wood.z,&spawn.x,&spawn.z,
            &bastion.x,&bastion.z,&fortress.x,&fortress.z,&extra)!=13) return 1;
        Family f={0}; Result out={0};
        uint64_t lower=(uint64_t)seed&MASK;
        if(!seed || !buried_candidate(lower,&f) || !buried_ravine(lower,&f)
            || !nearby(lower,Bastion,origin,96,&f.bastion) || !nearby(lower,Fortress,origin,256,&f.fortress)
            || !sister_check((uint64_t)seed,BURIED,&f,&g,NULL,&out)
            || !axes(main,f.finder_main,0) || !axes(entry,out.entry,0) || !axes(wood,out.wood,0)
            || !axes(spawn,out.spawn,0) || !axes(bastion,f.bastion,0) || !axes(fortress,f.fortress,0)) return 1;
        checked++;
    }
    if(!checked || ferror(stdin)) return 1;
    printf("PASS: %u mapless Overworld rows rechecked; no Minecraft worlds generated.\n",checked);
    return 0;
}

int main(int argc, char **argv) {
    if(argc==2 && !strcmp(argv[1],"--check-bank")) return check_bank();
    if(argc!=1) return 2;
    assert(type_of("buried_treasure")==BURIED);
    assert(!buried_resources((BuriedLoot){.iron=10}));
    assert(buried_resources((BuriedLoot){.iron=11}));
    assert(!buried_resources((BuriedLoot){.iron=9,.tnt=1}));
    assert(buried_resources((BuriedLoot){.iron=9,.tnt=2}));
    assert(!buried_resources((BuriedLoot){.iron=4,.diamonds=3,.gold=3}));
    assert(buried_resources((BuriedLoot){.iron=4,.diamonds=3,.gold=4}));
    assert(!buried_resources((BuriedLoot){.diamonds=64,.gold=64,.tnt=64}));
    MaplessRavine edge={.can_spawn=1,.radius=18,.lower_y=8,.upper_y=40,.x=-80,.z=80};
    assert(mapless_ravine_eligible(&edge,origin));
    edge.x=-80.00001; assert(!mapless_ravine_eligible(&edge,origin));
    edge.x=24; edge.z=24; assert(!mapless_ravine_eligible(&edge,origin));
    edge.x=25; assert(mapless_ravine_eligible(&edge,origin));
    edge.lower_y=9; assert(!mapless_ravine_eligible(&edge,origin));
    edge.lower_y=8; edge.upper_y=39; assert(!mapless_ravine_eligible(&edge,origin));
    assert(floor_div(-1,3)==-1 && floor_div(-4,3)==-2);
    int spawned=0;
    for(int i=0;i<4096;i++) {
        uint64_t seed=(uint64_t)i*STEP;
        int x=i%31-15, z=i%23-11;
        BuriedLoot loot=buried_loot(seed,x,z);
        BuriedLoot sister=buried_loot(seed^(UINT64_C(0xa35c)<<48),x,z);
        assert(loot.iron==sister.iron && loot.gold==sister.gold && loot.diamonds==sister.diamonds
            && loot.tnt==sister.tnt && loot.emeralds==sister.emeralds);
        MaplessRavine r=mapless_ravine(seed,x,z), s=mapless_ravine(seed^(UINT64_C(0xa35c)<<48),x,z);
        assert(r.can_spawn==s.can_spawn);
        if(r.can_spawn) {
            spawned++;
            mapless_ravine_middle(&r); mapless_ravine_middle(&s);
            assert(r.random==s.random && r.x==s.x && r.y==s.y && r.z==s.z);
        }
        printf("%d %d %d %d %d %d %d %d %.17g %.17g %.17g %.17g %d %d\n",i,
            loot.iron,loot.gold,loot.diamonds,loot.tnt,loot.emeralds,
            r.can_spawn,r.length,r.radius,r.x,r.y,r.z,r.lower_y,r.upper_y);
    }
    assert(spawned>30);
    return 0;
}
