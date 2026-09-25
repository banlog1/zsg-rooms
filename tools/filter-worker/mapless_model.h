#ifndef ZSG_MAPLESS_MODEL_H
#define ZSG_MAPLESS_MODEL_H
#include <math.h>
#include "model_loot.h"

/* Loot/resource rules: published MIT ZSG mapless + filter_common, pinned in THIRD_PARTY.md. */
typedef struct { int iron, gold, diamonds, tnt, emeralds; } BuriedLoot;

static BuriedLoot buried_loot(uint64_t seed, int chunk_x, int chunk_z) {
    uint64_t decorator, r;
    setSeed(&decorator,decoration(seed,chunk_x*16,chunk_z*16,30001));
    setSeed(&r,nextLong(&decorator));
    BuriedLoot loot={0};
    int rolls=count(&r,5,8);
    for(int i=0;i<rolls;i++) {
        int choice=nextInt(&r,35);
        if(choice<20) loot.iron+=count(&r,1,4);
        else if(choice<30) loot.gold+=count(&r,1,4);
        else loot.tnt+=count(&r,1,2);
    }
    rolls=count(&r,1,3);
    for(int i=0;i<rolls;i++) {
        int choice=nextInt(&r,15);
        if(choice<5) loot.emeralds+=count(&r,4,8);
        else if(choice<10) loot.diamonds+=count(&r,1,2);
        else count(&r,1,5);
    }
    return loot;
}

static int buried_resources(BuriedLoot loot) {
    if(loot.iron<1) return 0;
    loot.iron--;
    if(loot.diamonds>=3) loot.diamonds-=3;
    else if(loot.iron>=3) loot.iron-=3;
    else return 0;
    if(loot.iron<3) return 0;
    loot.iron-=3;
    if(loot.tnt>=1) {
        loot.tnt--;
        if(loot.iron>=2) loot.iron-=2;
        else if(loot.gold>=2) loot.gold-=2;
        else return 0;
    } else if(loot.iron>=3) loot.iron-=3;
    else if(loot.gold>=3) loot.gold-=3;
    else return 0;
    return loot.gold>=1 || loot.diamonds>=1 || loot.iron>=1 || loot.tnt>=1;
}

/* DuncanRuns' CC0 ravines.c, revision 81a4f3fbf77ffc97845b2326e927f706cd7c4f21.
 * Same midpoint model, with explicit left-to-right RNG draws and unused guesses removed.
 * This models carver geometry, not terrain intersection or a block-perfect open ravine. */
typedef struct {
    uint64_t random;
    int can_spawn, length, lower_y, upper_y;
    float yaw, pitch;
    double radius, x, y, z;
} MaplessRavine;

static MaplessRavine mapless_ravine(uint64_t seed, int chunk_x, int chunk_z) {
    MaplessRavine r={0};
    setSeed(&r.random,seed);
    uint64_t a=nextLong(&r.random);
    uint64_t b=nextLong(&r.random);
    setSeed(&r.random,(a*(uint64_t)(int64_t)chunk_x)^(b*(uint64_t)(int64_t)chunk_z)^seed);
    if(nextFloat(&r.random)>0.02) return r;
    r.can_spawn=1;
    r.x=(double)chunk_x*16+nextInt(&r.random,16);
    int bound=nextInt(&r.random,40)+8;
    r.y=nextInt(&r.random,bound)+20;
    r.z=(double)chunk_z*16+nextInt(&r.random,16);
    r.yaw=nextFloat(&r.random)*(float)(3.14159265358979323846*2);
    r.pitch=(nextFloat(&r.random)-0.5F)/4.0F;
    float first=nextFloat(&r.random), second=nextFloat(&r.random);
    r.radius=(1.5+(double)((first*2.0F+second)*2.0F))*3.0;
    r.length=112-nextInt(&r.random,28);
    return r;
}

static void mapless_ravine_middle(MaplessRavine *r) {
    uint64_t canyon=nextLong(&r->random);
    setSeed(&r->random,canyon);
    for(int y=0;y<256;y++) if(y==0 || nextInt(&r->random,3)==0) {
        nextFloat(&r->random); nextFloat(&r->random);
    }
    float yaw_shift=0, pitch_shift=0;
    for(int i=0;i<r->length/2;i++) {
        float horizontal=(float)cos(r->pitch), dy=(float)sin(r->pitch);
        nextFloat(&r->random); nextFloat(&r->random);
        r->x+=cos(r->yaw)*horizontal;
        r->y+=dy;
        r->z+=sin(r->yaw)*horizontal;
        r->pitch*=0.7F;
        r->pitch+=pitch_shift*0.05F;
        r->yaw+=yaw_shift*0.05F;
        pitch_shift*=0.8F;
        yaw_shift*=0.5F;
        float a=nextFloat(&r->random), b=nextFloat(&r->random), c=nextFloat(&r->random);
        pitch_shift+=(a-b)*c*2.0F;
        a=nextFloat(&r->random); b=nextFloat(&r->random); c=nextFloat(&r->random);
        yaw_shift+=(a-b)*c*4.0F;
        nextInt(&r->random,4);
    }
    r->lower_y=(int)(r->y-r->radius);
    r->upper_y=(int)(r->y+r->radius+1);
    if(r->lower_y<1) r->lower_y=1;
    if(r->upper_y>248) r->upper_y=248;
}

static int mapless_ravine_eligible(const MaplessRavine *r, Pos treasure) {
    double dx=fabs(r->x-treasure.x), dz=fabs(r->z-treasure.z);
    return r->can_spawn && r->radius>=18 && r->lower_y<=8 && r->upper_y>=40
        && dx<=80 && dz<=80 && !(dx<25 && dz<25);
}
#endif
