#define main zsg_search_main
#include "seed_finder.c"
#undef main
#include <assert.h>

/* Frozen pre-optimization query policies, retained only as test oracles. */
static int reference_wooded_land(Generator *g, SurfaceNoise *noise, Pos anchor, Pos *out) {
    for (int x=-80; x<=80; x+=16) for (int z=-80; z<=80; z+=16) {
        Pos p={anchor.x+x,anchor.z+z};
        if (!circle(p,anchor,80)) continue;
        int wooded=0;
        for (int dx=-8; dx<=8; dx+=8) for (int dz=-8; dz<=8; dz+=8) {
            int biome=getBiomeAt(g,4,floor_div(p.x+dx,4),16,floor_div(p.z+dz,4));
            int category=getCategory(MC_1_16_1,biome);
            if (category==forest || category==taiga || category==jungle) wooded++;
        }
        if (wooded<7 || height(g,noise,p)<65) continue;
        *out=p; return 1;
    }
    return 0;
}

static int reference_pool(Generator *g, SurfaceNoise *noise, const Lake *lake) {
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
    }
    return min>=64 && max<=80 && max-min<=6 && lake->y>=max+1;
}

static void test_temple_policy(void) {
    float terrain[16];
    // Exhaust every visible/hidden combination, including isolated tips and diagonals.
    for (unsigned mask=0;mask<65536;mask++) {
        for (int i=0;i<16;i++) terrain[i]=(mask&(1u<<i))?64:90;
        int expected=0;
        const int patches[9][4]={{0,1,4,5},{1,2,5,6},{2,3,6,7},{4,5,8,9},{5,6,9,10},
            {6,7,10,11},{8,9,12,13},{9,10,13,14},{10,11,14,15}};
        for (int p=0;p<9;p++) {
            int corners=0;
            for (int j=0;j<4;j++) corners+=!!(mask&(1u<<patches[p][j]));
            if (corners==4) expected=1;
        }
        assert(temple_roof_exposed(terrain)==expected);
    }
    for (int i=0;i<16;i++) terrain[i]=69;
    assert(temple_roof_exposed(terrain));
    for (int i=0;i<16;i++) terrain[i]=69.01f;
    assert(!temple_roof_exposed(terrain));
    for (int i=0;i<16;i++) terrain[i]=90;
    terrain[0]=terrain[1]=terrain[4]=65;
    terrain[5]=69;
    assert(temple_roof_exposed(terrain));
    terrain[0]=65.01f;
    assert(!temple_roof_exposed(terrain));
    for (int i=0;i<16;i++) terrain[i]=64;
    const float invalid[]={NAN,INFINITY,-INFINITY,-1,257};
    for (int i=0;i<5;i++) {
        terrain[4]=invalid[i];
        assert(!temple_roof_exposed(terrain));
    }
}

static void test_search_policies(void) {
    const unsigned hits[]={3,70,1030,20000};
    PolicyObservation observations[POLICY_COUNT]={0};
    PolicyTotal totals[POLICY_COUNT]={0};
    unsigned accepted=0;
    for (unsigned tried=1;tried<=20000;tried++) {
        int pass=accepted<4 && tried==hits[accepted];
        accepted+=pass;
        if (pass || !(tried&(tried-1))) policy_observe(observations,tried,accepted,7+tried*.25);
    }
    assert(policy_commit(totals,observations));
    for (int b=0;b<POLICY_BUDGETS;b++) for (int c=0;c<POLICY_CAPS;c++) {
        unsigned count=0, stop=policy_budgets[b];
        for (int h=0;h<4 && hits[h]<=policy_budgets[b];h++) {
            if (++count==policy_caps[c]) { stop=hits[h]; break; }
        }
        PolicyTotal *p=&totals[b*POLICY_CAPS+c];
        assert(p->accepted==count && p->productive==1 && p->ms==7+stop*.25);
    }
    memset(observations,0,sizeof(observations));
    policy_observe(observations,64,0,50);
    PolicyTotal unchanged[POLICY_COUNT];
    memcpy(unchanged,totals,sizeof(totals));
    assert(!policy_commit(totals,observations));
    assert(!memcmp(totals,unchanged,sizeof(totals)));
    memset(observations,0,sizeof(observations));
    for (unsigned tried=1;tried<=65536;tried*=2) policy_observe(observations,tried,0,7+tried*.25);
    assert(policy_commit(totals,observations));
    for (int b=0;b<POLICY_BUDGETS;b++) for (int c=0;c<POLICY_CAPS;c++) {
        int i=b*POLICY_CAPS+c;
        assert(totals[i].accepted==unchanged[i].accepted && totals[i].productive==1);
        assert(totals[i].ms==unchanged[i].ms+7+policy_budgets[b]*.25);
    }
}

/* Offline bank inspection: private seed/anchor input; only row numbers/heights leave stdout. */
static int inspect_surfaces(int mode) {
    Generator g;
    setupGenerator(&g,MC_1_16_1,0);
    SurfaceNoiseCache cache={0};
    int64_t seed;
    Pos anchor;
    int fields, index=0;
    while ((fields=scanf("%" SCNd64 " %d %d",&seed,&anchor.x,&anchor.z))==3) {
        if (anchor.x<-1000000 || anchor.x>1000000 || anchor.z<-1000000 || anchor.z>1000000) return 2;
        applySeed(&g,DIM_OVERWORLD,(uint64_t)seed);
        if (mode==2) {
            printf("{\"index\":%d,\"wooded\":%s}\n",index++,tree_biome(&g,anchor,20)?"true":"false");
            continue;
        }
        SurfaceNoise *noise=surface_noise_for_seed(&cache,(uint64_t)seed);
        if (mode==1) {
            Pos water;
            int found=nearby_water(&g,noise,anchor,&water);
            printf("{\"index\":%d,\"found\":%s,\"water\":",index++,found?"true":"false");
            if (found) printf("[%d,%d]",water.x,water.z); else printf("null");
            printf("}\n");
            continue;
        }
        float terrain[16];
        if (mapApproxHeight(terrain,NULL,&g,noise,floor_div(anchor.x+4,4),floor_div(anchor.z+4,4),4,4)) return 3;
        printf("{\"index\":%d,\"exposed\":%s,\"heights\":[",index++,temple_roof_exposed(terrain)?"true":"false");
        for (int i=0;i<16;i++) printf("%s%.3f",i?",":"",terrain[i]);
        printf("]}\n");
    }
    return fields==EOF?0:2;
}

int main(int argc,char **argv) {
    if (argc==2 && !strcmp(argv[1],"--temple-exposure")) return inspect_surfaces(0);
    if (argc==2 && !strcmp(argv[1],"--nearby-water")) return inspect_surfaces(1);
    if (argc==2 && !strcmp(argv[1],"--temple-wood")) return inspect_surfaces(2);
    if (argc!=1) return 2;
    test_temple_policy();
    test_search_policies();
    int wet_biomes[]={river,ocean,cold_ocean,deep_lukewarm_ocean};
    float wet_heights[]={61,60,55,50};
    assert(water_patch(wet_biomes,wet_heights));
    for (int mask=0;mask<16;mask++) {
        int patch[4];
        for (int i=0;i<4;i++) patch[i]=(mask&(1<<i))?river:desert;
        assert(water_patch(patch,wet_heights)==(mask==15));
    }
    const int excluded[]={frozen_river,frozen_ocean,deep_frozen_ocean,desert,swamp,-1};
    for (int i=0;i<6;i++) {
        wet_biomes[0]=excluded[i];
        assert(!water_patch(wet_biomes,wet_heights));
    }
    wet_biomes[0]=river;
    const float invalid_water[]={61.01f,63,80,-1,NAN,INFINITY};
    for (int i=0;i<6;i++) {
        wet_heights[0]=invalid_water[i];
        assert(!water_patch(wet_biomes,wet_heights));
    }
    Generator g;
    setupGenerator(&g,MC_1_16_1,0);
    SurfaceNoiseCache cache={0};
    int wood_passes=0, pool_passes=0, water_passes=0, fallback_tests=0, ship_passes=0, temple_wood_rejections=0;
    for (int i=0;i<256;i++) {
        uint64_t seed=(i+1024)*STEP;
        applySeed(&g,DIM_OVERWORLD,seed);
        SurfaceNoise *noise=surface_noise_for_seed(&cache,seed);
        Pos anchor={(i%17-8)*16+(i%4),(i%19-9)*16+(i%3)};
        if (isViableStructurePos(Desert_Pyramid,&g,anchor.x,anchor.z,0) && !tree_biome(&g,anchor,20)) {
            Family temple={0};
            temple.zsg_search_main=anchor;
            Result result={0};
            uint64_t rejected_before=failed[BIOMES], surface_before=reached[SURFACE];
            assert(!sister_check(seed,TEMPLE,&temple,&g,&cache,&result));
            // A dry temple biome must reject before any pool is considered, even without lake candidates.
            assert(failed[BIOMES]==rejected_before+1 && reached[SURFACE]==surface_before);
            temple_wood_rejections++;
        }
        if (i<64) {
            Pos water={0,0}, repeated={0,0};
            int found=nearby_water(&g,noise,anchor,&water);
            assert(nearby_water(&g,noise,anchor,&repeated)==found);
            assert(water.x==repeated.x && water.z==repeated.z);
            water_passes+=found;
            if (found) {
                assert(circle(anchor,water,48));
                int ids[4]; float terrain[4];
                for (int z=0;z<2;z++) for (int x=0;x<2;x++) {
                    Pos p={water.x-2+4*x,water.z-2+4*z};
                    ids[z*2+x]=getBiomeAt(&g,4,floor_div(p.x,4),16,floor_div(p.z,4));
                    terrain[z*2+x]=height(&g,noise,p);
                }
                assert(water_patch(ids,terrain));
                Pos entry={0,0}, chosen_water={0,0};
                Pos pools[]={{anchor.x+256,anchor.z+256},anchor};
                Pos distant_main={anchor.x-512,anchor.z-512};
                assert(select_watered_pool(&g,noise,distant_main,anchor,pools,2,&entry,&chosen_water));
                assert(entry.x==anchor.x && entry.z==anchor.z);
                assert(chosen_water.x==water.x && chosen_water.z==water.z);
                assert(!select_watered_pool(&g,noise,distant_main,anchor,pools,1,&entry,&chosen_water));
                assert(!select_watered_pool(&g,noise,anchor,anchor,pools,0,&entry,&chosen_water));
                // A dry first candidate must not hide a later wet pool (same world/seed).
                for (int offset=128;offset<=512 && !fallback_tests;offset+=128) {
                    pools[0]=(Pos){anchor.x+offset,anchor.z};
                    Pos unused;
                    if (!nearby_water(&g,noise,pools[0],&unused)) {
                        assert(select_watered_pool(&g,noise,anchor,anchor,pools,2,&entry,&chosen_water));
                        assert(entry.x==anchor.x && entry.z==anchor.z);
                        fallback_tests++;
                    }
                }
            }
        }
        // Batched terrain sampling must match the existing point query, including negative coordinates.
        float roof_terrain[16];
        for (int z=0;z<4;z++) for (int x=0;x<4;x++) {
            Pos p={anchor.x+4+4*x,anchor.z+4+4*z};
            roof_terrain[z*4+x]=height(&g,noise,p);
        }
        int roof_pass=temple_roof_exposed(roof_terrain);
        assert(temple_exposure(&g,noise,anchor)==roof_pass);
        assert(temple_exposure(&g,noise,anchor)==roof_pass);
        Pos old_wood={0,0},new_wood={0,0};
        int old_pass=reference_wooded_land(&g,noise,anchor,&old_wood);
        int new_pass=wooded_land(&g,noise,anchor,&new_wood);
        assert(old_pass==new_pass);
        assert(old_wood.x==new_wood.x && old_wood.z==new_wood.z);
        wood_passes+=new_pass;
        Family ship={0};
        ship.zsg_search_main=anchor; // The include's main macro also renames this field.
        for (int x=-80;x<=80;x+=16) for (int z=-80;z<=80;z+=16)
            ship.ravines[ship.ravine_count++]=(Pos){anchor.x+x,anchor.z+z};
        Pos reference_entry={0,0};
        int reference_pass=0;
        if (old_pass) for (int r=0;r<ship.ravine_count;r++) {
            Pos p=ship.ravines[r], toward={(p.x*2+anchor.x)/3,(p.z*2+anchor.z)/3};
            if (isDeepOcean(getBiomeAt(&g,4,floor_div(p.x,4),16,floor_div(p.z,4)))
                && isDeepOcean(getBiomeAt(&g,4,floor_div(toward.x,4),16,floor_div(toward.z,4)))) {
                reference_entry=p; reference_pass=1; break;
            }
        }
        Result ship_result={0};
        assert(ship_surface(&g,noise,&ship,&ship_result)==reference_pass);
        if (reference_pass) {
            assert(ship_result.entry.x==reference_entry.x && ship_result.entry.z==reference_entry.z);
            assert(ship_result.wood.x==old_wood.x && ship_result.wood.z==old_wood.z);
            ship_passes++;
        }
        ship.ravine_count=0;
        assert(!ship_surface(&g,noise,&ship,&ship_result));
        for (int salt=10000;salt<=10001;salt++) for (int y=64;y<=128;y+=16) {
            Lake lake={anchor,y,salt};
            old_pass=reference_pool(&g,noise,&lake);
            new_pass=pool_proxy(&g,noise,&lake);
            assert(old_pass==new_pass);
            pool_passes+=new_pass;
        }
    }
    assert(wood_passes>0 && wood_passes<256);
    assert(pool_passes>0 && pool_passes<2560);
    assert(water_passes>0 && water_passes<64 && fallback_tests>0);
    assert(ship_passes>0 && ship_passes<256);
    assert(temple_wood_rejections>0);
    printf("Temple wood policy passed: %d biome-valid temples rejected at the structure before pool search.\n",temple_wood_rejections);
    printf("Surface query parity passed: 256 wooded searches and 2560 pool checks (%d/%d passes).\n",wood_passes,pool_passes);
    printf("Temple exposure passed: 65536 masks, boundary/invalid heights and 256 deterministic terrain grids.\n");
    printf("Water policy passed: biome/height boundaries, 64 deterministic searches and alternative-pool selection (%d wet).\n",water_passes);
    printf("Ship surface parity passed: 256 searches with original anchor/ravine order, plus empty lists (%d passes).\n",ship_passes);
    printf("Search policy accounting passed: 18 budget/cap combinations, unsuccessful and interrupted families.\n");
    return 0;
}
