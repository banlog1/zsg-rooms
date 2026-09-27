#include "aa_end_model.h"
#include <assert.h>
#include <stdio.h>

static void assert_cluster(const AaEndResult *result) {
    assert(result->count==AA_END_SHIPS);
    assert(aa_end_in_radius(result->ships[0],result->outer_gateway,AA_END_FIRST_SHIP_RADIUS));
    for(int j=1;j<result->count;j++) {
        assert(aa_end_in_radius(result->ships[j],result->ships[0],AA_END_CONNECTION_RADIUS)
            || (j==2 && aa_end_in_radius(result->ships[j],result->ships[1],AA_END_CONNECTION_RADIUS)));
        for(int k=0;k<j;k++)
            assert(!aa_end_in_radius(result->cities[j],result->cities[k],0));
    }
}

static void test_selection(void) {
    AaEndResult result={0};
    AaEndCandidate boundary[]={{{0,0},{512,0}},{{320,0},{1536,0}},{{640,0},{2560,0}}};
    assert(aa_end_select(boundary,3,&result));
    assert_cluster(&result);
    boundary[1].ship.z=1;
    assert(!aa_end_select(boundary,3,&result));
    assert(result.count==0);
    boundary[1].ship.z=0;
    boundary[0].ship.x=513;
    assert(!aa_end_select(boundary,3,&result));

    AaEndCandidate later[]={{{0,0},{-512,0}},{{320,0},{512,0}},
        {{640,0},{1536,0}},{{-320,0},{2560,0}}};
    // Ignore an out-of-range first candidate and find a later eligible anchor.
    later[0].ship.z=-1;
    assert(aa_end_select(later,4,&result));
    assert_cluster(&result);
    assert(result.ships[0].x==512 && result.ships[0].z==0);

    AaEndCandidate chain[]={{{0,0},{512,0}},{{320,0},{1536,0}},{{640,0},{2560,0}}};
    assert(aa_end_select(chain,3,&result));
    assert_cluster(&result);
    assert(!aa_end_in_radius(result.ships[2],result.ships[0],AA_END_CONNECTION_RADIUS));
    // Preserve opposite-branch clusters, even without a direct second-to-third hop.
    AaEndCandidate star[]={{{0,0},{512,0}},{{320,0},{512,-1024}},{{640,0},{512,1024}}};
    assert(aa_end_select(star,3,&result));
    assert_cluster(&result);
    star[2].ship.z++;
    assert(!aa_end_select(star,3,&result));
    AaEndCandidate duplicate[]={{{0,0},{100,0}},{{0,0},{110,0}},{{320,0},{120,0}}};
    assert(!aa_end_select(duplicate,3,&result));
    assert(!aa_end_select(NULL,0,&result));
}

int main(void) {
    test_selection();
    assert(aa_end_floor_div(-1,16)==-1 && aa_end_floor_div(-17,16)==-2);
    assert(aa_end_in_radius((Pos){0,0},(Pos){512,0},AA_END_FIRST_SHIP_RADIUS));
    assert(!aa_end_in_radius((Pos){0,0},(Pos){512,1},AA_END_FIRST_SHIP_RADIUS));
    for(int i=0;i<128;i++) {
        uint64_t seed=(uint64_t)i*UINT64_C(0x9e3779b97f4a7c15);
        Pos gateways[20], city, ship={0};
        getFixedEndGateways(MC_1_16_1,seed,gateways);
        int rx=i%11-5, rz=i%13-6;
        rx+=rx<0?-4:4; // Keep parity vectors outside the central no-city radius.
        assert(getStructurePos(End_City,MC_1_16_1,seed,rx,rz,&city));
        Piece pieces[END_CITY_PIECES_MAX];
        int n=getEndCityPieces(pieces,seed,aa_end_floor_div(city.x,16),aa_end_floor_div(city.z,16));
        int ships=0;
        for(int j=0;j<n;j++) if(pieces[j].type==END_SHIP) { ships++; ship=aa_end_ship_center(&pieces[j]); }
        assert(ships<=1);
        Generator g;
        setupGenerator(&g,MC_1_16_1,0);
        applySeed(&g,DIM_END,seed);
        SurfaceNoise noise;
        initSurfaceNoise(&noise,DIM_END,seed);
        int biome=!!isViableStructurePos(End_City,&g,city.x,city.z,0);
        int terrain=isViableEndCityTerrain(&g,&noise,city.x,city.z);
        if(i<16) {
            AaEndCache cache={0};
            int pass=aa_end_cached(seed,&cache);
            AaEndResult sister;
            assert(pass==aa_end_check(seed^(UINT64_C(0xa35c)<<48),&sister));
            assert(!memcmp(&cache.result,&sister,sizeof(sister)));
            assert(pass==(cache.result.count==AA_END_SHIPS));
            // A cached verdict is reused, not recalculated for another sister.
            assert(pass==aa_end_cached(seed^(UINT64_C(0x1234)<<48),&cache));
            if(pass) assert_cluster(&cache.result);
        }
        printf("%d %d %d %d %d %d %d %d %d %d %d\n",i,city.x,city.z,biome,terrain,n,ships,ship.x,ship.z,gateways[0].x,gateways[0].z);
    }
    // Fixed synthetic corpus fixtures exercise both verdicts without searching in tests.
    AaEndCache positive={0}, negative={0};
    uint64_t positive_seed=UINT64_C(3870)*UINT64_C(0x9e3779b97f4a7c15);
    assert(aa_end_cached(positive_seed,&positive));
    assert(!aa_end_cached(0,&negative));
    AaEndResult sister;
    assert(aa_end_check(positive_seed^(UINT64_C(0xa35c)<<48),&sister));
    assert(!memcmp(&positive.result,&sister,sizeof(sister)));
    assert_cluster(&positive.result);
    return 0;
}
