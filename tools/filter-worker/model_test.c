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
    assert(type_of("aa_temple")==AA_TEMPLE);
    assert(temple_type(TEMPLE) && temple_type(AA_TEMPLE) && !temple_type(VILLAGE));
    assert(!aa_gunpowder_pass((Loot){.gunpowder=21}));
    assert(aa_gunpowder_pass((Loot){.gunpowder=22}));
    assert(aa_gunpowder_pass((Loot){.gunpowder=23}));
    assert(circle(origin,(Pos){70,0},AA_VILLAGE_RADIUS));
    assert(!circle(origin,(Pos){70,1},AA_VILLAGE_RADIUS));
    assert(circle(origin,(Pos){0,-1024},AA_TEMPLE_RADIUS));
    assert(!circle(origin,(Pos){1,-1024},AA_TEMPLE_RADIUS));
    for(int iron=0;iron<=4;iron++) for(int picks=0;picks<=2;picks++) for(int diamonds=0;diamonds<=6;diamonds++) {
        int expected=iron>=4 || (iron>=1 && (picks>=1 || diamonds>=3));
        assert(village_resources(iron,picks,diamonds)==expected);
    }
    Family smith={0};
    assert(parse_smith_loot("SMITH_LOOT 1 1 0\n",&smith));
    assert(smith.loot.iron==1 && smith.iron_pickaxes==1 && smith.loot.diamonds==0);
    assert(parse_smith_loot("SMITH_LOOT 1 0 3\n",&smith));
    assert(smith.loot.iron==1 && smith.iron_pickaxes==0 && smith.loot.diamonds==3);
    const char *invalid[]={"IRON 4\n","SMITH_LOOT 4\n","SMITH_LOOT 4 0 0 extra\n",
        "SMITH_LOOT -1 1 3\n","SMITH_LOOT 1 -1 3\n","SMITH_LOOT 1 0 -3\n",
        "SMITH_LOOT 100001 0 0\n","SMITH_LOOT 0 100001 0\n","SMITH_LOOT 0 0 100001\n"};
    for(unsigned i=0;i<sizeof(invalid)/sizeof(invalid[0]);i++) assert(!parse_smith_loot(invalid[i],&smith));
    for(int i=0;i<512;i++) {
        uint64_t seed=(uint64_t)i*STEP;
        Pos pos={((i%31)-15)*16,((i%23)-11)*16};
        Loot temple=temple_loot(seed,pos), ship=ship_loot(seed,pos);
        Loot aa=temple_loot_model(seed,pos,1);
        Loot aa_sister=temple_loot_model(seed ^ (UINT64_C(0xa35c)<<48),pos,1);
        assert(aa.iron==temple.iron && aa.diamonds==temple.diamonds && aa.gold==temple.gold);
        assert(aa.gunpowder==aa_sister.gunpowder && aa.gunpowder>=0 && aa.gunpowder<=128);
        Loot sister=temple_loot(seed ^ (UINT64_C(0xa35c)<<48),pos);
        assert(temple.iron==sister.iron && temple.diamonds==sister.diamonds && temple.gold==sister.gold);
        Family a={0},b={0};
        lake_attempts(seed,pos,&a);lake_attempts(seed ^ (UINT64_C(0xabcd)<<48),pos,&b);
        assert(a.lake_count==b.lake_count);
        for(int j=0;j<a.lake_count;j++) {
            assert(a.lakes[j].pos.x==b.lakes[j].pos.x && a.lakes[j].pos.z==b.lakes[j].pos.z && a.lakes[j].y==b.lakes[j].y);
            assert(circle(a.lakes[j].pos,pos,96));
        }
        Pos candidates[AA_CANDIDATE_CAP], sisters[AA_CANDIDATE_CAP], anchor;
        assert(getStructurePos(Desert_Pyramid,MC_1_16_1,seed,i%5-2,i%7-3,&anchor));
        int n=aa_candidates(seed,Desert_Pyramid,anchor,AA_TEMPLE_RADIUS,candidates);
        assert(n==aa_candidates(seed ^ (UINT64_C(0xabcd)<<48),Desert_Pyramid,anchor,AA_TEMPLE_RADIUS,sisters));
        for(int j=0;j<n;j++) {
            assert(circle(candidates[j],anchor,AA_TEMPLE_RADIUS));
            assert(candidates[j].x!=anchor.x || candidates[j].z!=anchor.z);
            assert(candidates[j].x==sisters[j].x && candidates[j].z==sisters[j].z);
            for(int k=0;k<j;k++) assert(candidates[j].x!=candidates[k].x || candidates[j].z!=candidates[k].z);
        }
        if(i<32) {
            Generator g;
            setupGenerator(&g,MC_1_16_1,0);
            applySeed(&g,DIM_OVERWORLD,seed);
            Family f={0}; Result result={0};
            f.aa_temple_count=n;
            memcpy(f.aa_temples,candidates,n*sizeof(Pos));
            int viable=0;
            for(int j=0;j<n;j++) viable+=!!isViableStructurePos(Desert_Pyramid,&g,candidates[j].x,candidates[j].z,0);
            assert(aa_temples_check(&g,&f,&result)==(viable>=AA_EXTRA_TEMPLES));
            // The full-seed gate must never accept fewer than two opportunities.
            f.aa_temple_count=viable?1:0;
            assert(!aa_temples_check(&g,&f,&result));
            f.aa_village_count=0;
            assert(!aa_village_check(seed,&g,&f,&result));
        }
        // Test vector indexes and resources only. No numeric seeds leave this process.
        printf("%d %d %d %d %d %d %d %d %d\n",i,temple.iron,temple.diamonds,temple.gold,ship.iron,ship.diamonds,ship.gold,ship.food,aa.gunpowder);
    }
    return 0;
}
