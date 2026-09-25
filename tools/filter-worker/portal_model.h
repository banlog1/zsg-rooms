#ifndef ZSG_PORTAL_MODEL_H
#define ZSG_PORTAL_MODEL_H
#include "model_loot.h"

/* ZSG filter_common.zig and regular village portal predicates (MIT).
 * This standalone route has no village, Looting or stronghold restriction. */
typedef struct { int obsidian, nuggets, flint, steel, charges, axes, pickaxes; } PortalLoot;
#define PORTAL_MIN_FIRE_CHARGES 5

static PortalLoot portal_loot(uint64_t seed, Pos pos) {
    static const int sizes[] = {10,9,6,6,6,12,9,11,9};
    static const int levels[9][12] = {
        {5,5,5,2,2,3,3,3,1,1}, {5,5,5,5,1,3,3,1,1},
        {5,1,3,3,1,1}, {5,1,3,3,1,1}, {5,1,3,3,1,1},
        {4,4,4,4,4,3,3,2,1,3,1,1}, {4,4,4,4,3,1,3,1,1},
        {4,4,4,4,3,1,3,1,3,1,1}, {4,4,4,4,3,1,3,1,1}
    };
    uint64_t decorator, r;
    setSeed(&decorator,decoration(seed,pos.x,pos.z,40005));
    setSeed(&r,nextLong(&decorator));
    PortalLoot loot={0};
    int rolls=count(&r,4,8);
    for(int i=0;i<rolls;i++) {
        int c=nextInt(&r,398);
        if(c<40) loot.obsidian+=count(&r,1,2);
        else if(c<80) loot.flint+=count(&r,1,4);
        else if(c<120) loot.nuggets+=count(&r,9,18);
        else if(c<160) loot.steel++;
        else if(c<200) loot.charges++;
        else if(c<215) { /* Golden apple: fixed count. */ }
        else if(c<230) count(&r,4,24);
        else if(c<365) {
            int item=(c-230)/15;
            if(item==1) loot.axes++;
            if(item==4) loot.pickaxes++;
            int level=levels[item][nextInt(&r,sizes[item])];
            if(level>1) nextInt(&r,level);
        } else if(c<370 || (c>=380 && c<385)) count(&r,4,12);
        else if(c>=390 && c<395) count(&r,2,8);
        else if(c>=397) count(&r,1,2);
    }
    return loot;
}

static int portal_has_tool(PortalLoot loot) {
    return loot.axes>0 || loot.pickaxes>0;
}

static int portal_resources(PortalLoot loot) {
    /* Necessary only. Frame, surviving lava, bucket budget and water are checked
     * per sister. Obsidian-only repairs must not require bucket iron. */
    return loot.steel>0 || loot.charges>=PORTAL_MIN_FIRE_CHARGES || (loot.nuggets>=9 && loot.flint>0);
}

static int portal_variant(uint64_t seed, Pos pos, int biome) {
    StructureVariant v={0};
    return getVariant(&v,Ruined_Portal,MC_1_16_1,seed,pos.x,pos.z,biome)
        && !v.underground && v.airpocket;
}

static int portal_biome(int biome) {
    return biome==forest || biome==birch_forest || biome==dark_forest || biome==flower_forest
        || biome==tall_birch_forest || biome==plains || biome==sunflower_plains
        || biome==savanna || biome==taiga || biome==giant_tree_taiga;
}
#endif
