#ifndef ZSG_AA_END_MODEL_H
#define ZSG_AA_END_MODEL_H
#include "finders.h"
#include <string.h>

enum {
    AA_END_SHIPS=3, AA_END_FIRST_SHIP_RADIUS=512, AA_END_CONNECTION_RADIUS=1024,
    AA_END_SHIP_SEARCH_RADIUS=AA_END_FIRST_SHIP_RADIUS+2*AA_END_CONNECTION_RADIUS,
    AA_END_CITY_SEARCH_RADIUS=AA_END_SHIP_SEARCH_RADIUS+384
};
typedef struct { Pos city, ship; } AaEndCandidate;
typedef struct {
    Pos inner_gateway, outer_gateway;
    Pos cities[AA_END_SHIPS], ships[AA_END_SHIPS];
    int count;
} AaEndResult;
typedef struct { int checked, pass; AaEndResult result; } AaEndCache;

static int aa_end_floor_div(int x, int d) { return x/d - (x%d<0); }
static int aa_end_in_radius(Pos a, Pos b, int radius) {
    int64_t x=(int64_t)a.x-b.x, z=(int64_t)a.z-b.z;
    return x*x+z*z <= (int64_t)radius*radius;
}

/* Try every first/second pair; the third may connect to either preceding ship. */
static int aa_end_select(const AaEndCandidate *candidates, int count, AaEndResult *out) {
    out->count=0;
    memset(out->cities,0,sizeof(out->cities));
    memset(out->ships,0,sizeof(out->ships));
    for(int i=0;i<count;i++) {
        if(!aa_end_in_radius(candidates[i].ship,out->outer_gateway,AA_END_FIRST_SHIP_RADIUS)) continue;
        for(int j=0;j<count;j++) {
            if(aa_end_in_radius(candidates[j].city,candidates[i].city,0)
                || !aa_end_in_radius(candidates[j].ship,candidates[i].ship,AA_END_CONNECTION_RADIUS)) continue;
            for(int k=0;k<count;k++) {
                if(aa_end_in_radius(candidates[k].city,candidates[i].city,0)
                    || aa_end_in_radius(candidates[k].city,candidates[j].city,0)) continue;
                if(!aa_end_in_radius(candidates[k].ship,candidates[i].ship,AA_END_CONNECTION_RADIUS)
                    && !aa_end_in_radius(candidates[k].ship,candidates[j].ship,AA_END_CONNECTION_RADIUS)) continue;
                int selected[AA_END_SHIPS]={i,j,k};
                for(int n=0;n<AA_END_SHIPS;n++) {
                    out->cities[n]=candidates[selected[n]].city;
                    out->ships[n]=candidates[selected[n]].ship;
                }
                out->count=AA_END_SHIPS;
                return 1;
            }
        }
    }
    return 0;
}

/* Horizontal template center, not the city start. */
static Pos aa_end_ship_center(const Piece *p) {
    Pos result={p->pos.x,p->pos.z};
    switch(p->rot) {
        case 0: result.x+=6; result.z+=14; break;
        case 1: result.x-=14; result.z+=6; break;
        case 2: result.x-=6; result.z-=14; break;
        case 3: result.x+=14; result.z-=6; break;
    }
    return result;
}

static int aa_end_check(uint64_t seed, AaEndResult *out) {
    memset(out,0,sizeof(*out));
    Generator g;
    setupGenerator(&g,MC_1_16_1,0);
    applySeed(&g,DIM_END,seed);
    SurfaceNoise noise;
    initSurfaceNoise(&noise,DIM_END,seed);
    Pos gateways[20];
    getFixedEndGateways(MC_1_16_1,seed,gateways);
    out->inner_gateway=gateways[0];
    out->outer_gateway=getLinkedGatewayPos(&g.en,&noise,seed,gateways[0]);

    StructureConfig config;
    if(!getStructureConfig(End_City,MC_1_16_1,&config)) return 0;
    int size=config.regionSize*16;
    Pos center=out->outer_gateway;
    int min_x=aa_end_floor_div(center.x-AA_END_CITY_SEARCH_RADIUS,size);
    int max_x=aa_end_floor_div(center.x+AA_END_CITY_SEARCH_RADIUS,size);
    int min_z=aa_end_floor_div(center.z-AA_END_CITY_SEARCH_RADIUS,size);
    int max_z=aa_end_floor_div(center.z+AA_END_CITY_SEARCH_RADIUS,size);
    // One city per region bounds the candidate array for this search envelope.
    AaEndCandidate candidates[(max_x-min_x+1)*(max_z-min_z+1)];
    int candidate_count=0;
    Piece pieces[END_CITY_PIECES_MAX];
    for(int x=min_x;x<=max_x;x++) {
        for(int z=min_z;z<=max_z;z++) {
            Pos city;
            if(!getStructurePos(End_City,MC_1_16_1,seed,x,z,&city)
                || !aa_end_in_radius(city,center,AA_END_CITY_SEARCH_RADIUS)
                || !isViableStructurePos(End_City,&g,city.x,city.z,0)) continue;
            int count=getEndCityPieces(pieces,seed,aa_end_floor_div(city.x,16),aa_end_floor_div(city.z,16));
            for(int i=0;i<count;i++) {
                if(pieces[i].type!=END_SHIP) continue;
                Pos ship=aa_end_ship_center(&pieces[i]);
                if(!aa_end_in_radius(ship,center,AA_END_SHIP_SEARCH_RADIUS)) continue;
                if(!isViableEndCityTerrain(&g,&noise,city.x,city.z)) break;
                candidates[candidate_count++]=(AaEndCandidate){city,ship};
                if(candidate_count>=AA_END_SHIPS && aa_end_select(candidates,candidate_count,out)) return 1;
                break; // Count distinct cities, never multiple pieces of one city.
            }
        }
    }
    return 0;
}

/* End terrain, layout and the first gateway use only Java Random's lower48. */
static int aa_end_cached(uint64_t seed, AaEndCache *cache) {
    if(!cache->checked) {
        cache->pass=aa_end_check(seed,&cache->result);
        cache->checked=1;
    }
    return cache->pass;
}
#endif
