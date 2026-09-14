#define main finder_main
#include "seed_finder.c"
#undef main
#include <assert.h>

int main(void) {
    assert(floor_div(-1,16)==-1 && floor_div(-16,16)==-1 && floor_div(-17,16)==-2);
    assert(!ship_resources((Loot){.iron=11,.food=29}));
    assert(ship_resources((Loot){.iron=11,.food=30}));
    assert(!ship_resources((Loot){.iron=10,.food=30}));
    assert(type_of("village-temple")==-1);
    for(int i=0;i<512;i++) {
        uint64_t seed=(uint64_t)i*STEP;
        Pos pos={((i%31)-15)*16,((i%23)-11)*16};
        Loot temple=temple_loot(seed,pos), ship=ship_loot(seed,pos);
        Loot sister=temple_loot(seed ^ (UINT64_C(0xa35c)<<48),pos);
        assert(temple.iron==sister.iron && temple.diamonds==sister.diamonds && temple.gold==sister.gold);
        Family a={0},b={0};
        lake_attempts(seed,pos,&a);lake_attempts(seed ^ (UINT64_C(0xabcd)<<48),pos,&b);
        assert(a.lake_count==b.lake_count);
        for(int j=0;j<a.lake_count;j++) {
            assert(a.lakes[j].pos.x==b.lakes[j].pos.x && a.lakes[j].pos.z==b.lakes[j].pos.z && a.lakes[j].y==b.lakes[j].y);
            assert(circle(a.lakes[j].pos,pos,96));
        }
        // Test vector indexes and resources only. No numeric seeds leave this process.
        printf("%d %d %d %d %d %d %d %d\n",i,temple.iron,temple.diamonds,temple.gold,ship.iron,ship.diamonds,ship.gold,ship.food);
    }
    return 0;
}
