#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <math.h>
#include <errno.h>
#ifdef _WIN32
#include <windows.h>
#endif
#include "model_loot.h"
#include "mapless_model.h"
#include "portal_model.h"
#include "surface_noise_cache.h"
#include "search_policy.h"

#define PROFILE "zsg-model-only-v5"
#define PIN "e61f90580cbdd883214a8054670dacae655e59c0"
#define MASK UINT64_C(0xffffffffffff)
#define STEP UINT64_C(0x9e3779b97f4a7c15)
enum { TEMPLE, SHIP, VILLAGE, BURIED, PORTAL };
enum { GEOMETRY, LAYOUT_LOOT, NETHER, FEATURES, NETHER_MODEL, BIOMES, SPAWN, SURFACE, TEMPLE_EXPOSURE, WATER, SMITH_LOOT, ACCEPT, FOREST, PORTAL_FRAME, STAGES };
static const char *names[] = {"geometry", "layout_loot", "nether", "feature_attempts", "nether_model", "biomes", "spawn", "surface_proxy", "temple_exposure", "nearby_water", "smith_loot", "accepted", "forest_size", "portal_completion"};
enum { WATER_RADIUS=48, WATER_STEP=4, WATER_GRID=2*WATER_RADIUS/WATER_STEP+2 };
static uint64_t reached[STAGES], failed[STAGES];
static double stage_ms[STAGES];
static int trace_enabled;
static double family_started;
static uint64_t decision_trace=UINT64_C(0xcbf29ce484222325);

/* Opt-in regression digest: compare every visited family's/sister's decision, not just winners. */
static void trace_decision(uint64_t seed, int stage, int pass) {
    if (!trace_enabled) return;
    decision_trace=(decision_trace^seed)*UINT64_C(0x100000001b3);
    decision_trace=(decision_trace^(uint64_t)(stage*2+!!pass))*UINT64_C(0x100000001b3);
}
typedef struct { Pos pos; int y, salt; } Lake;
typedef struct { Pos main, bastion, fortress; Lake lakes[512]; int lake_count; Pos ravines[225]; int ravine_count; Loot loot; BuriedLoot buried; PortalLoot portal; int iron_pickaxes; int obsidian_score; char bastion_type[16]; } Family;
typedef struct { Pos spawn, entry, wood, water; int portal_status, portal_missing, portal_lava, portal_cast, portal_template, portal_y; } Result;

static int parse_portal(const char *response, Result *out) {
    char extra;
    int n=sscanf(response,"PORTAL_RESULT %d %d %d %d %d %d %c",&out->portal_status,&out->portal_missing,
        &out->portal_lava,&out->portal_cast,&out->portal_template,&out->portal_y,&extra);
    if(n!=6 || out->portal_status<0 || out->portal_status>4 || out->portal_missing<0 || out->portal_missing>84
        || out->portal_lava<0 || out->portal_lava>33 || out->portal_cast<0 || out->portal_cast>out->portal_missing
        || out->portal_template<0 || out->portal_template>13 || out->portal_y<0 || out->portal_y>255) return 0;
    if(out->portal_status>=3 && (!out->portal_template || !out->portal_y)) return 0;
    return out->portal_status==3 ? out->portal_cast>0 && out->portal_lava>=out->portal_cast
        : out->portal_status!=4 || out->portal_cast==0;
}

static double now_ms(void) {
#ifdef _WIN32
    LARGE_INTEGER value, frequency;
    QueryPerformanceCounter(&value); QueryPerformanceFrequency(&frequency);
    return value.QuadPart * 1000.0 / frequency.QuadPart;
#else
    struct timespec value;
    clock_gettime(CLOCK_MONOTONIC, &value);
    return value.tv_sec * 1000.0 + value.tv_nsec / 1000000.0;
#endif
}
static int done(int stage, double start, int pass) { stage_ms[stage] += now_ms() - start; reached[stage]++; if (!pass) failed[stage]++; return pass; }
static int axes(Pos a, Pos b, int radius) { return abs(a.x-b.x) <= radius && abs(a.z-b.z) <= radius; }
static int circle(Pos a, Pos b, int radius) { int dx=a.x-b.x, dz=a.z-b.z; return dx*dx+dz*dz <= radius*radius; }
static int village_resources(int iron, int iron_pickaxes, int diamonds) {
    return iron >= 4 || (iron >= 1 && (iron_pickaxes > 0 || diamonds >= 3));
}
static int parse_smith_loot(const char *response, Family *f) {
    int iron, picks, diamonds;
    char extra;
    if (sscanf(response,"SMITH_LOOT %d %d %d %c",&iron,&picks,&diamonds,&extra)!=3 ||
        iron<0 || iron>100000 || picks<0 || picks>100000 || diamonds<0 || diamonds>100000) return 0;
    f->loot.iron=iron;
    f->loot.diamonds=diamonds;
    f->iron_pickaxes=picks;
    return 1;
}
static int floor_div(int x, int d) { return x/d - (x%d < 0); }
static const Pos origin = {0,0};

static int nearby(uint64_t seed, int feature, Pos center, int radius, Pos *out) {
    StructureConfig config;
    if (!getStructureConfig(feature, MC_1_16_1, &config)) return 0;
    int size = config.regionSize * 16;
    for (int x=floor_div(center.x-radius,size); x<=floor_div(center.x+radius,size); x++) {
        for (int z=floor_div(center.z-radius,size); z<=floor_div(center.z+radius,size); z++) {
            if (getStructurePos(feature, MC_1_16_1, seed, x, z, out) && axes(*out,center,radius)) return 1;
        }
    }
    return 0;
}

static int wood_biome(int biome) {
    int category = getCategory(MC_1_16_1, biome);
    return category == forest || category == jungle || category == taiga || category == swamp || category == savanna;
}

static int tree_biome(Generator *g, Pos p, int radius) {
    for (int x=-radius; x<=radius; x+=radius) for (int z=-radius; z<=radius; z+=radius) {
        if (wood_biome(getBiomeAt(g, 4, floor_div(p.x+x,4), 16, floor_div(p.z+z,4)))) return 1;
    }
    return 0;
}

static void lake_attempts(uint64_t seed, Pos anchor, Family *f) {
    f->lake_count = 0;
    for (int x=floor_div(anchor.x-112,16); x<=floor_div(anchor.x+96,16); x++) {
        for (int z=floor_div(anchor.z-112,16); z<=floor_div(anchor.z+96,16); z++) {
            for (int salt=10000; salt<=10001; salt++) {
                uint64_t r;
                setSeed(&r, decoration(seed,x*16,z*16,salt));
                if (nextInt(&r,8) != 0) continue;
                int dx=nextInt(&r,16), dz=nextInt(&r,16);
                int y=nextInt(&r,nextInt(&r,248)+8);
                if (y < 64 || nextInt(&r,10) != 0) continue;
                Pos center = {x*16+dx+8,z*16+dz+8};
                if (!circle(center,anchor,96) || !axes(center,origin,224)) continue;
                if (f->lake_count == 512) { fprintf(stderr,"Lake capacity exceeded\n"); exit(3); }
                f->lakes[f->lake_count++] = (Lake){center,y,salt};
            }
        }
    }
}

/* GoATS-style initial carver proxy, not ZSG's unavailable midpoint implementation. */
static void ravine_attempts(uint64_t seed, Pos anchor, Family *f) {
    f->ravine_count = 0;
    for (int x=floor_div(anchor.x-80,16); x<=floor_div(anchor.x+80,16); x++) {
        for (int z=floor_div(anchor.z-80,16); z<=floor_div(anchor.z+80,16); z++) {
            uint64_t r = chunkGenerateRnd(seed,x,z);
            if (nextFloat(&r) >= .02f) continue;
            Pos p = {x*16+nextInt(&r,16),0};
            int y = 20+nextInt(&r,nextInt(&r,40)+8);
            p.z = z*16+nextInt(&r,16);
            nextFloat(&r); nextFloat(&r);
            float first = nextFloat(&r);
            float width = 2*(2*first+nextFloat(&r));
            if (y<25 && width>5 && axes(p,anchor,80) && !axes(p,anchor,25)) f->ravines[f->ravine_count++] = p;
        }
    }
}

static int buried_candidate(uint64_t seed, Family *f) {
    /* Preserve ZSG's first loot-qualified treasure in its ordered 21x21 chunk scan. */
    for(int x=-10;x<=10;x++) for(int z=-10;z<=10;z++) {
        Pos pos;
        if(!getStructurePos(Treasure,MC_1_16_1,seed,x,z,&pos)) continue;
        BuriedLoot loot=buried_loot(seed,x,z);
        if(!buried_resources(loot)) continue;
        f->main=pos; f->buried=loot;
        f->loot=(Loot){.iron=loot.iron,.diamonds=loot.diamonds,.gold=loot.gold};
        return 1;
    }
    return 0;
}

static int buried_ravine(uint64_t seed, Family *f) {
    int cx=floor_div(f->main.x,16), cz=floor_div(f->main.z,16);
    for(int x=cx-7;x<=cx+7;x++) for(int z=cz-7;z<=cz+7;z++) {
        MaplessRavine r=mapless_ravine(seed,x,z);
        if(!r.can_spawn || r.radius<18) continue;
        mapless_ravine_middle(&r);
        if(!mapless_ravine_eligible(&r,f->main)) continue;
        f->ravines[0]=(Pos){(int)r.x,(int)r.z}; f->ravine_count=1;
        return 1;
    }
    return 0;
}

static int family_check(uint64_t seed, int type, Family *f, Generator *g) {
    double start=now_ms();
    family_started=start;
    int feature=type==PORTAL?Ruined_Portal:type==SHIP?Shipwreck:type==TEMPLE?Desert_Pyramid:Village;
    int radius=type==SHIP?208:type==TEMPLE?320:224;
    int pass=nearby(seed,Bastion,origin,96,&f->bastion) && nearby(seed,Fortress,origin,256,&f->fortress)
        && (type==BURIED || (getStructurePos(feature,MC_1_16_1,seed,0,0,&f->main) && axes(f->main,origin,radius)));
    if (!done(GEOMETRY,start,pass)) return 0;
    start=now_ms();
    // Failed geometry never consumes candidate data. Arrays are overwritten up to their counts.
    f->lake_count=0;
    f->ravine_count=0;
    memset(&f->loot,0,sizeof(f->loot));
    pass=1;
    if (type==BURIED) {
        pass=buried_candidate(seed,f);
    } else if (type==PORTAL) {
        pass=portal_variant(seed,f->main,plains);
        if(pass) {
            f->portal=portal_loot(seed,f->main);
            f->loot.iron=f->portal.nuggets/9;
            pass=portal_has_tool(f->portal) && portal_resources(f->portal);
        }
    } else if (pass && type==SHIP) {
        uint64_t r=chunkGenerateRnd(seed,f->main.x/16,f->main.z/16);
        int rotation=nextInt(&r,4), layout=nextInt(&r,20);
        pass=rotation==3 && (layout==0 || layout==7 || layout==10 || layout==17);
        if (pass) { f->loot=ship_loot(seed,f->main); pass=ship_resources(f->loot); }
    } else if (pass && type==TEMPLE) {
        f->loot=temple_loot(seed,f->main);
        pass=f->loot.iron >= (f->loot.diamonds>=3?4:7);
    }
    if (!done(LAYOUT_LOOT,start,pass)) return 0;
    start=now_ms();
    applySeed(g,DIM_NETHER,seed);
    pass=isViableStructurePos(Bastion,g,f->bastion.x,f->bastion.z,0)
        && isViableStructurePos(Fortress,g,f->fortress.x,f->fortress.z,0);
    if (!done(NETHER,start,pass)) return 0;
    start=now_ms();
    if (type==BURIED) pass=buried_ravine(seed,f);
    else if (type==PORTAL) pass=1;
    else if (type==SHIP) { ravine_attempts(seed,f->main,f); pass=f->ravine_count>0; }
    else { lake_attempts(seed,f->main,f); pass=f->lake_count>0; }
    if (!done(FEATURES,start,pass)) return 0;
    start=now_ms();
    /* ZSG's complete Nether checks, once per lower48 family, over a private model pipe. */
    printf("NETHER %" PRIu64 " %d %d %d %d\n",seed,floor_div(f->bastion.x,16),floor_div(f->bastion.z,16),
        floor_div(f->fortress.x,16),floor_div(f->fortress.z,16));
    fflush(stdout);
    char response[96], extra;
    int status;
    if (!fgets(response,sizeof(response),stdin)
        || sscanf(response,"NETHER %d %d %15s %c",&status,&f->obsidian_score,f->bastion_type,&extra)!=3
        || status<0 || status>3 || f->obsidian_score<0 || f->obsidian_score>127
        || (status>=2 && f->obsidian_score<20)
        || (strcmp(f->bastion_type,"STABLES") && strcmp(f->bastion_type,"BRIDGE")
            && strcmp(f->bastion_type,"HOUSING") && strcmp(f->bastion_type,"TREASURE"))) {
        fprintf(stderr,"Nether model protocol failed; search aborted\n"); exit(3);
    }
    return done(NETHER_MODEL,start,status==3);
}

static float height(Generator *g, SurfaceNoise *noise, Pos p) {
    float value;
    mapApproxHeight(&value,NULL,g,noise,floor_div(p.x,4),floor_div(p.z,4),1,1);
    return value;
}

/* 1.16.1 temples stay at Y64. Their central stepped roof is rotation-symmetric.
 * Sample offsets 4,8,12,16 on each axis; require a connected 4x4-block patch,
 * not merely one exposed tip. Two blocks of clearance plus a provisional
 * one-block model allowance is a gameplay proxy, not a visibility guarantee. */
static int temple_roof_exposed(const float terrain[16]) {
    unsigned clear=0;
    for (int i=0;i<16;i++) {
        if (!isfinite(terrain[i]) || terrain[i]<0 || terrain[i]>256) return 0;
        int x=i%4, z=i/4;
        int roof_y=(x==1 || x==2) && (z==1 || z==2)?72:68;
        if (terrain[i]<=roof_y-3) clear|=1u<<i;
    }
    for (int z=0;z<3;z++) for (int x=0;x<3;x++) {
        unsigned patch=0x33u<<(z*4+x); // Adjacent corners: 0,1,4,5.
        if ((clear&patch)==patch) return 1;
    }
    return 0;
}

static int temple_exposure(Generator *g, SurfaceNoise *noise, Pos anchor) {
    float terrain[16];
    if (mapApproxHeight(terrain,NULL,g,noise,floor_div(anchor.x+4,4),floor_div(anchor.z+4,4),4,4)) return 0;
    return temple_roof_exposed(terrain);
}

static int wooded_patch(const int *biomes, int width, int x, int z) {
    int misses=0;
    for (int dx=-2; dx<=2; dx+=2) for (int dz=-2; dz<=2; dz+=2) {
        int biome=biomes[(z+dz)*width+x+dx];
        int category=getCategory(MC_1_16_1,biome);
        if (category!=forest && category!=taiga && category!=jungle && ++misses>2) return 0;
    }
    return 1; // Seven of nine samples must be wooded; three misses cannot recover.
}

static int wooded_land(Generator *g, SurfaceNoise *noise, Pos anchor, Pos *out) {
    // All overlapping 3x3 patches use this one map, with the original sample order.
    enum { WIDTH=45 };
    Range range={4,floor_div(anchor.x-88,4),floor_div(anchor.z-88,4),WIDTH,WIDTH,16,1};
    int *biomes=allocCache(g,range);
    if (!biomes) { fprintf(stderr,"Wood model allocation failed\n"); exit(3); }
    if (genBiomes(g,biomes,range)) { free(biomes); return 0; }
    for (int x=-80; x<=80; x+=16) for (int z=-80; z<=80; z+=16) {
        Pos p={anchor.x+x,anchor.z+z};
        if (!circle(p,anchor,80)) continue;
        if (!wooded_patch(biomes,WIDTH,(x+88)/4,(z+88)/4) || height(g,noise,p)<65) continue;
        *out=p; free(biomes); return 1;
    }
    free(biomes);
    return 0;
}

static int ship_surface(Generator *g, SurfaceNoise *noise, const Family *f, Result *out) {
    // The first qualifying ravine is independent of the wooded-land anchor.
    for (int i=0;i<f->ravine_count;i++) {
        Pos p=f->ravines[i], toward={(p.x*2+f->main.x)/3,(p.z*2+f->main.z)/3};
        if (isDeepOcean(getBiomeAt(g,4,floor_div(p.x,4),16,floor_div(p.z,4)))
            && isDeepOcean(getBiomeAt(g,4,floor_div(toward.x,4),16,floor_div(toward.z,4)))) {
            if (!wooded_land(g,noise,f->main,&out->wood)) return 0;
            out->entry=p; return 1;
        }
    }
    return 0;
}

static int pool_proxy(Generator *g, SurfaceNoise *noise, const Lake *lake) {
    /* Feature list is chosen at the chunk's noise biome, not at the lake center. */
    int cx=floor_div(lake->pos.x-8,16), cz=floor_div(lake->pos.z-8,16);
    int biome=getBiomeAt(g,4,cx*4+2,2,cz*4+2);
    int category=getCategory(MC_1_16_1,biome);
    if (lake->salt != (category==desert?10000:10001)) return 0;
    if (category!=desert && biome!=plains && biome!=savanna) return 0;
    float min=300,max=-1;
    const int offsets[5][2]={{0,0},{-8,0},{8,0},{0,-8},{0,8}};
    for (int i=0;i<5;i++) {
        Pos p={lake->pos.x+offsets[i][0],lake->pos.z+offsets[i][1]};
        int b=getBiomeAt(g,4,floor_div(p.x,4),16,floor_div(p.z,4));
        if (isOceanic(b) || getCategory(MC_1_16_1,b)==river) return 0;
        float y=height(g,noise,p); if(y<min)min=y; if(y>max)max=y;
        // These failures are monotonic as samples accumulate; later terrain cannot rescue the pool.
        if (min<64 || max>80 || max-min>6 || lake->y<max+1) return 0;
    }
    return min>=64 && max<=80 && max-min<=6 && lake->y>=max+1;
}

static int liquid_water_biome(int biome) {
    return biome==river || (isOceanic(biome) && biome!=frozen_ocean && biome!=deep_frozen_ocean);
}

static int water_patch(const int biomes[4], const float terrain[4]) {
    for (int i=0;i<4;i++) {
        if (!liquid_water_biome(biomes[i]) || !isfinite(terrain[i]) || terrain[i]<0 || terrain[i]>61) return 0;
    }
    return 1;
}

/* Four corners of a 4x4-block patch, below sea level with a model allowance.
 * Biomes are batched once; only promising unfrozen-water patches pay for heights.
 * Expanding grid rings prefer nearby water without claiming exact shore access. */
static int nearby_water(Generator *g, SurfaceNoise *noise, Pos anchor, Pos *out) {
    Range range={4,floor_div(anchor.x-WATER_RADIUS-2,4),floor_div(anchor.z-WATER_RADIUS-2,4),WATER_GRID,WATER_GRID,16,1};
    int *biomes=allocCache(g,range);
    if (!biomes) { fprintf(stderr,"Water model allocation failed\n"); exit(3); }
    if (genBiomes(g,biomes,range)) { free(biomes); return 0; }
    for (int ring=0;ring<=WATER_RADIUS/WATER_STEP;ring++) {
        for (int dz=-ring;dz<=ring;dz++) for (int dx=-ring;dx<=ring;dx++) {
            if ((abs(dx)!=ring && abs(dz)!=ring) || (dx*dx+dz*dz)*WATER_STEP*WATER_STEP>WATER_RADIUS*WATER_RADIUS) continue;
            int x=dx+WATER_RADIUS/WATER_STEP, z=dz+WATER_RADIUS/WATER_STEP;
            int i=z*WATER_GRID+x;
            int patch[]={biomes[i],biomes[i+1],biomes[i+WATER_GRID],biomes[i+WATER_GRID+1]};
            if (!liquid_water_biome(patch[0]) || !liquid_water_biome(patch[1])
                || !liquid_water_biome(patch[2]) || !liquid_water_biome(patch[3])) continue;
            float terrain[4];
            if (!mapApproxHeight(terrain,NULL,g,noise,range.x+x,range.z+z,2,2) && water_patch(patch,terrain)) {
                *out=(Pos){anchor.x+dx*WATER_STEP,anchor.z+dz*WATER_STEP};
                free(biomes); return 1;
            }
        }
    }
    free(biomes); return 0;
}

static int select_watered_pool(Generator *g, SurfaceNoise *noise, Pos main, Pos spawn,
    const Pos *pools, int count, Pos *entry, Pos *water) {
    for (int i=0;i<count;i++) {
        if ((axes(spawn,main,48) || axes(spawn,pools[i],48)) && nearby_water(g,noise,pools[i],water)) {
            *entry=pools[i]; return 1;
        }
    }
    return 0;
}

static int buried_sister(uint64_t seed, const Family *f, Generator *g, Result *out) {
    double start=now_ms();
    applySeed(g,DIM_OVERWORLD,seed);
    int pass=isViableStructurePos(Treasure,g,f->main.x,f->main.z,0)!=0;
    int forest_found=0;
    Pos wood={0};
    /* Fix the upstream inner-loop reset and explicitly require the forest sample. */
    if(pass) for(int x=f->main.x-10;x<=f->main.x+10 && !forest_found;x+=10) {
        for(int z=f->main.z-10;z<=f->main.z+10;z+=10) {
            if(getBiomeAt(g,1,x,255,z)==forest) { wood=(Pos){x,z}; forest_found=1; break; }
        }
    }
    if(!done(BIOMES,start,pass && forest_found)) return 0;
    start=now_ms();
    Pos ravine=f->ravines[0];
    Pos toward={floor_div(2*ravine.x+f->main.x,3),floor_div(2*ravine.z+f->main.z,3)};
    pass=isDeepOcean(getBiomeAt(g,1,ravine.x,64,ravine.z))
        && isDeepOcean(getBiomeAt(g,1,toward.x,64,toward.z));
    if(!done(SURFACE,start,pass)) return 0;
    start=now_ms();
    out->spawn=getSpawn(g);
    if(!done(SPAWN,start,axes(out->spawn,f->main,32))) return 0;
    start=now_ms();
    Range range={1,wood.x-20,wood.z-20,41,41,255,1};
    int *biomes=allocCache(g,range);
    if(!biomes) { fprintf(stderr,"Forest model allocation failed\n"); exit(3); }
    int forest_count=0;
    if(!genBiomes(g,biomes,range)) for(int i=0;i<41*41;i++) if(biomes[i]==forest) forest_count++;
    free(biomes);
    if(!done(FOREST,start,forest_count>=400)) return 0;
    out->wood=wood; out->entry=ravine;
    return 1;
}

static int portal_sister(uint64_t seed, Family *f, Generator *g, SurfaceNoiseCache *surface, Result *out) {
    double start=now_ms();
    applySeed(g,DIM_OVERWORLD,seed);
    int biome=getBiomeAt(g,4,floor_div(f->main.x,4)+2,255,floor_div(f->main.z,4)+2);
    int pass=portal_biome(biome) && portal_variant(seed,f->main,biome)
        && isViableStructurePos(Ruined_Portal,g,f->main.x,f->main.z,0);
    Pos wood=f->main;
    if(pass) {
        pass=0;
        for(int x=-30;x<=30 && !pass;x+=30) for(int z=-30;z<=30;z+=30) {
            Pos sample={f->main.x+x,f->main.z+z};
            if(wood_biome(getBiomeAt(g,1,sample.x,255,sample.z))) {wood=sample;pass=1;break;}
        }
    }
    if(!done(BIOMES,start,pass)) return 0;
    start=now_ms();
    out->spawn=getSpawn(g);
    if(!done(SPAWN,start,axes(out->spawn,f->main,32))) return 0;
    start=now_ms();
    printf("PORTAL %" PRId64 " %d %d %d %d %d %d %d\n",(int64_t)seed,floor_div(f->main.x,16),
        floor_div(f->main.z,16),f->portal.obsidian,f->portal.nuggets,f->portal.flint,f->portal.steel,f->portal.charges);
    fflush(stdout);
    char response[128];
    if(!fgets(response,sizeof(response),stdin) || !parse_portal(response,out)) {
        fprintf(stderr,"Portal model protocol failed; search aborted\n"); exit(3);
    }
    if(!done(PORTAL_FRAME,start,out->portal_status>=3)) return 0;
    if(out->portal_status==3) {
        start=now_ms();
        if(!done(WATER,start,nearby_water(g,surface_noise_for_seed(surface,seed),f->main,&out->water))) return 0;
    }
    out->entry=f->main; out->wood=wood;
    return 1;
}

static int sister_check(uint64_t seed, int type, Family *f, Generator *g, SurfaceNoiseCache *surface, Result *out) {
    if(type==BURIED) return buried_sister(seed,f,g,out);
    if(type==PORTAL) return portal_sister(seed,f,g,surface,out);
    double start=now_ms();
    applySeed(g,DIM_OVERWORLD,seed);
    int feature=type==SHIP?Shipwreck:type==TEMPLE?Desert_Pyramid:Village;
    int biome=isViableStructurePos(feature,g,f->main.x,f->main.z,0);
    int pass=biome!=0;
    if (pass && type==SHIP) {
        // The lower48 layout/loot model uses the ocean template list, not the beach list.
        pass=isOceanic(getBiomeAt(g,4,floor_div(f->main.x,4)+2,0,floor_div(f->main.z,4)+2));
    }
    if (pass && type==VILLAGE) {
        StructureVariant variant;
        pass=(biome==plains || biome==desert || biome==savanna)
            && getVariant(&variant,Village,MC_1_16_1,seed,f->main.x,f->main.z,biome) && !variant.abandoned
            && tree_biome(g,f->main,30);
    }
    // Temple routes collect wood before heading to a pool; anchor this check at the temple.
    if (pass && type==TEMPLE) pass=tree_biome(g,f->main,20);
    if (!done(BIOMES,start,pass)) return 0;
    start=now_ms();
    SurfaceNoise *noise=surface_noise_for_seed(surface,seed);
    Pos pools[512];
    int pool_count=0;
    pass=0;
    if(type==SHIP) {
        pass=ship_surface(g,noise,f,out);
    } else for(int i=0;i<f->lake_count;i++) {
        Lake *lake=&f->lakes[i];
        if(pool_proxy(g,noise,lake) && (type==TEMPLE || tree_biome(g,lake->pos,20))) {
            pools[pool_count++]=lake->pos;pass=1;
        }
    }
    if (!done(SURFACE,start,pass)) return 0;
    // Measure spawn only after the cheaper terrain proxy has a usable candidate.
    start=now_ms();
    out->spawn=getSpawn(g);
    pass=axes(out->spawn,f->main,type==SHIP?64:48);
    if(type!=SHIP) {
        int near_main=pass;
        pass=0;
        for(int i=0;i<pool_count;i++) if(near_main || axes(out->spawn,pools[i],48)) {
            out->entry=pools[i];out->wood=type==TEMPLE?f->main:pools[i];pass=1;break;
        }
    }
    if (!done(SPAWN,start,pass)) return 0;
    // Only pay for exposure on otherwise accepted temples, not every biome-valid sister.
    if (type==TEMPLE) {
        start=now_ms();
        if (!done(TEMPLE_EXPOSURE,start,temple_exposure(g,noise,f->main))) return 0;
    }
    if (type!=SHIP) {
        start=now_ms();
        pass=select_watered_pool(g,noise,f->main,out->spawn,pools,pool_count,&out->entry,&out->water);
        if (!done(WATER,start,pass)) return 0;
        out->wood=type==TEMPLE?f->main:out->entry;
    }
    if (type==VILLAGE) {
        start=now_ms();
        /* Private parent-child pipe, never forwarded by the Java coordinator. */
        printf("SMITH %" PRId64 " %d %d\n",(int64_t)seed,f->main.x/16,f->main.z/16);
        fflush(stdout);
        char response[64];
        if (!fgets(response,sizeof(response),stdin) || !parse_smith_loot(response,f)) {
            fprintf(stderr,"Village model protocol failed; search aborted\n"); exit(3);
        }
        return done(SMITH_LOOT,start,village_resources(f->loot.iron,f->iron_pickaxes,f->loot.diamonds));
    }
    return 1;
}

static int type_of(const char *name) {
    if(!strcmp(name,"temple"))return TEMPLE;
    if(!strcmp(name,"shipwreck"))return SHIP;
    if(!strcmp(name,"village"))return VILLAGE;
    if(!strcmp(name,"buried_treasure"))return BURIED;
    if(!strcmp(name,"ruined_portal"))return PORTAL;
    return -1;
}
static uint64_t number(const char *s) {
    char *end; if(!*s || *s=='-') {fprintf(stderr,"Invalid numeric argument\n");exit(2);}
    errno=0;
    uint64_t n=strtoull(s,&end,10); if(*end || errno==ERANGE) {fprintf(stderr,"Invalid numeric argument\n");exit(2);} return n;
}

int main(int argc,char **argv) {
    if((argc!=9 && argc!=10) || strcmp(argv[1],"search")) {
        fprintf(stderr,"Usage: seed-finder search temple|shipwreck|village|buried_treasure|ruined_portal families sisters target seconds stream private-output.jsonl [family-cap]\n"); return 2;
    }
    int type=type_of(argv[2]);
    uint64_t limit=number(argv[3]), sisters=number(argv[4]), target=number(argv[5]), seconds=number(argv[6]), stream=number(argv[7]);
    unsigned family_cap=argc==10?(unsigned)number(argv[9]):1;
    if (argc==10 && number(argv[9])>4) return 2;
    int tune=getenv("ZSG_MODEL_TUNE") && !strcmp(getenv("ZSG_MODEL_TUNE"),"1");
    if (!family_cap || family_cap>4 || (tune && (sisters!=65536 || family_cap!=4))) return 2;
    if(type<0 || !limit || limit>MASK || !sisters || sisters>65536 || !target || target>1000000 || !seconds || seconds>86400) return 2;
    if(!getenv("ZSG_MODEL_PIPE") || strcmp(getenv("ZSG_MODEL_PIPE"),"1")) {
        fprintf(stderr,"Searches require the private standalone Java coordinator\n"); return 2;
    }
    trace_enabled=getenv("ZSG_MODEL_TRACE") && !strcmp(getenv("ZSG_MODEL_TRACE"),"1");
    FILE *output=fopen(argv[8],"wx"); /* Refuse to overwrite an existing bank. */
    if(!output) {fprintf(stderr,"Cannot create a new private output file\n");return 3;}
    Generator g; setupGenerator(&g,MC_1_16_1,0);
    SurfaceNoiseCache surface={0};
    Family f={0}; Result result={0};
    double start=now_ms(), deadline=start+seconds*1000;
    uint64_t families=0, checked=0, accepted=0, digest=UINT64_C(0xcbf29ce484222325);
    uint64_t accepted_families=0, profiled=0, incomplete=0;
    PolicyTotal policies[POLICY_COUNT]={0};
    double profiled_ms=0;
    uint64_t next_lower=(stream*STEP)&MASK;
    while(families<limit && accepted<target && now_ms()<deadline) {
        uint64_t lower=next_lower; next_lower=(next_lower+STEP)&MASK; families++;
        int family_pass=family_check(lower,type,&f,&g);
        trace_decision(lower,0,family_pass);
        if(!family_pass)continue;
        PolicyObservation observations[POLICY_COUNT]={0};
        unsigned family_accepted=0;
        uint32_t upper_start=(uint32_t)(lower>>16)&65535, upper_step=((uint32_t)(lower>>32)|1)&65535;
        for(uint64_t i=0;i<sisters && accepted<target && now_ms()<deadline;i++) {
            uint64_t seed=lower|((uint64_t)((upper_start+i*upper_step)&65535)<<48); checked++;
            // Minecraft's text input treats zero as random; it still occupies its sister slot.
            int sister_pass=seed && sister_check(seed,type,&f,&g,&surface,&result);
            if (seed) trace_decision(seed,1,sister_pass);
            if (sister_pass) family_accepted++;
            if (tune && (sister_pass || !((i+1)&i)))
                policy_observe(observations,(unsigned)(i+1),family_accepted,now_ms()-family_started);
            if(!sister_pass)continue;
            if (family_accepted==1) accepted_families++;
            reached[ACCEPT]++;accepted++;
            char water_json[64]="null";
            char family_json[64]="";
            char smith_json[512]="";
            if (type==PORTAL) snprintf(smith_json,sizeof(smith_json),",\"ruinedPortalRule\":\"frame-completable-v3\",\"portalObsidian\":%d,\"ironNuggets\":%d,\"flint\":%d,\"flintAndSteel\":%d,\"fireCharges\":%d,\"goldenAxes\":%d,\"goldenPickaxes\":%d,\"portalMissingBlocks\":%d,\"portalLavaSources\":%d,\"portalCastBlocks\":%d,\"portalTemplate\":%d,\"portalY\":%d",f.portal.obsidian,f.portal.nuggets,f.portal.flint,f.portal.steel,f.portal.charges,f.portal.axes,f.portal.pickaxes,result.portal_missing,result.portal_lava,result.portal_cast,result.portal_template,result.portal_y);
            if (type==BURIED) snprintf(smith_json,sizeof(smith_json),",\"buriedTreasureRule\":\"mapless-regular-v1\",\"tnt\":%d,\"emeralds\":%d",f.buried.tnt,f.buried.emeralds);
            if (type==VILLAGE) snprintf(smith_json,sizeof(smith_json),",\"villageResourceRule\":\"pickaxe-credit-v1\",\"ironPickaxes\":%d",f.iron_pickaxes);
            if (family_cap>1) snprintf(family_json,sizeof(family_json),",\"family\":\"%" PRIu64 "\"",lower);
            if (type==TEMPLE || type==VILLAGE || (type==PORTAL && result.portal_status==3)) snprintf(water_json,sizeof(water_json),"[%d,%d]",result.water.x,result.water.z);
            /* A complete model acceptance, not a pending Minecraft verification job. */
            if(fprintf(output,"{\"profile\":\"" PROFILE "\",\"status\":\"MODEL_ACCEPTED\",\"type\":\"%s\",\"seed\":\"%" PRId64 "\","
                "\"structure\":[%d,%d],\"entry\":[%d,%d],\"wood\":[%d,%d],\"spawn\":[%d,%d],\"bastion\":[%d,%d],\"fortress\":[%d,%d],\"water\":%s,"
                "\"chestIron\":%d,\"diamonds\":%d,\"gold\":%d,\"foodScore\":%d,\"netherObsidianScore\":%d,\"bastionType\":\"%s\"%s%s}\n",
                argv[2],(int64_t)seed,f.main.x,f.main.z,result.entry.x,result.entry.z,result.wood.x,result.wood.z,result.spawn.x,result.spawn.z,
                f.bastion.x,f.bastion.z,f.fortress.x,f.fortress.z,water_json,f.loot.iron,f.loot.diamonds,f.loot.gold,f.loot.food,
                f.obsidian_score,f.bastion_type,family_json,smith_json)<0 || fflush(output)) {
                fclose(output);fprintf(stderr,"Private bank write failed\n");return 3;
            }
            digest=(digest^seed)*UINT64_C(0x100000001b3);
            if (family_accepted>=family_cap) break;
        }
        if (tune) {
            profiled_ms+=now_ms()-family_started;
            if (policy_commit(policies,observations)) profiled++;
            else incomplete++;
        }
    }
    if(fclose(output))return 3;
    printf("{\"profile\":\"" PROFILE "\",\"backend\":\"standalone-cubiomes\",\"revision\":\"" PIN "\",\"minecraftWorlds\":0,"
        "\"type\":\"%s\",\"families\":%" PRIu64 ",\"sisters\":%" PRIu64 ",\"accepted\":%" PRIu64 ",\"elapsedMs\":%.3f,"
        "\"stop\":\"%s\",\"digest\":\"%016" PRIx64 "\",\"checks\":{",argv[2],families,checked,accepted,now_ms()-start,
        accepted>=target?"TARGET":families>=limit?"FAMILY_LIMIT":"TIME_LIMIT",digest);
    for(int i=0;i<STAGES;i++)printf("%s\"%s\":{\"reached\":%" PRIu64 ",\"rejected\":%" PRIu64 ",\"ms\":%.3f}",i?",":"",names[i],reached[i],failed[i],stage_ms[i]);
    printf("},\"decisionTrace\":\"%016" PRIx64 "\",\"traceEnabled\":%s,\"familyCap\":%u,\"acceptedFamilies\":%" PRIu64,
        decision_trace,trace_enabled?"true":"false",family_cap,accepted_families);
    if (tune) {
        double shared_ms=now_ms()-start-profiled_ms;
        printf(",\"policyCompletedFamilies\":%" PRIu64 ",\"policyInterruptedFamilies\":%" PRIu64 ",\"policyEstimates\":[",profiled,incomplete);
        for (int b=0;b<POLICY_BUDGETS;b++) for (int c=0;c<POLICY_CAPS;c++) {
            int i=b*POLICY_CAPS+c;
            printf("%s{\"sisters\":%u,\"familyCap\":%u,\"accepted\":%" PRIu64 ",\"productiveFamilies\":%" PRIu64 ",\"estimatedMs\":%.3f}",
                i?",":"",policy_budgets[b],policy_caps[c],policies[i].accepted,policies[i].productive,shared_ms+policies[i].ms);
        }
        printf("]");
    }
    printf("}\n");return 0;
}
