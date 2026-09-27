#include "aa_end_model.h"
#include <errno.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>

/* Offline diagnostics only; never changes finder acceptance or generates a world. */
int main(int argc, char **argv) {
    if(argc!=2 || !*argv[1]) return 2;
    char *end;
    errno=0;
    int64_t signed_seed=strtoll(argv[1],&end,10);
    if(*end || errno==ERANGE) return 2;
    uint64_t seed=(uint64_t)signed_seed;
    AaEndCache original={0};
    int pass=aa_end_cached(seed,&original);
    Pos center=original.result.outer_gateway;
    enum { SHIP_RADIUS=3072, CITY_RADIUS=SHIP_RADIUS+384 };
    Generator g;
    setupGenerator(&g,MC_1_16_1,0);
    applySeed(&g,DIM_END,seed);
    SurfaceNoise noise;
    initSurfaceNoise(&noise,DIM_END,seed);
    StructureConfig config;
    if(!getStructureConfig(End_City,MC_1_16_1,&config)) return 3;
    int size=config.regionSize*16, ships=0;
    Piece pieces[END_CITY_PIECES_MAX];
    printf("{\"seed\":\"%" PRId64 "\",\"currentPassed\":%s,\"outerGateway\":[%d,%d],"
        "\"shipSearchRadius\":%d,\"citySearchRadius\":%d,\"ships\":[",
        signed_seed,pass?"true":"false",center.x,center.z,SHIP_RADIUS,CITY_RADIUS);
    for(int x=aa_end_floor_div(center.x-CITY_RADIUS,size);
            x<=aa_end_floor_div(center.x+CITY_RADIUS,size);x++) {
        for(int z=aa_end_floor_div(center.z-CITY_RADIUS,size);
                z<=aa_end_floor_div(center.z+CITY_RADIUS,size);z++) {
            Pos city;
            if(!getStructurePos(End_City,MC_1_16_1,seed,x,z,&city)
                || !aa_end_in_radius(city,center,CITY_RADIUS)
                || !isViableStructurePos(End_City,&g,city.x,city.z,0)) continue;
            int count=getEndCityPieces(pieces,seed,aa_end_floor_div(city.x,16),aa_end_floor_div(city.z,16));
            for(int i=0;i<count;i++) {
                if(pieces[i].type!=END_SHIP) continue;
                Pos ship=aa_end_ship_center(&pieces[i]);
                if(!aa_end_in_radius(ship,center,SHIP_RADIUS)) continue;
                if(!isViableEndCityTerrain(&g,&noise,city.x,city.z)) break;
                printf("%s{\"city\":[%d,%d],\"ship\":[%d,%d]}",
                    ships++?",":"",city.x,city.z,ship.x,ship.z);
                break;
            }
        }
    }
    printf("]}\n");
    return fflush(stdout) || ferror(stdout) ? 3 : 0;
}
