#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "generator.h"
#include "surface_noise_cache.h"

int main(void) {
    SurfaceNoiseCache cache = {0};
    SurfaceNoise reference;
    Generator generator;
    setupGenerator(&generator, MC_1_16_1, 0);
    int checks = 0;
    for (int family=0; family<128; family++) {
        uint64_t lower = (family * UINT64_C(0x9e3779b97f4a7c15)) & UINT64_C(0xffffffffffff);
        for (int sister=0; sister<4; sister++) {
            uint64_t seed = lower | ((uint64_t)(sister * 21845) << 48);
            initSurfaceNoise(&reference, DIM_OVERWORLD, seed);
            SurfaceNoise *cached = surface_noise_for_seed(&cache, seed);
            assert(cache.initialized && cache.lower48 == lower);
            assert(cached == &cache.noise);
            for (int pos=0; pos<8; pos++) {
                int x=(pos-4)*7, y=pos*4, z=(family%11-5)*3;
                double expected = sampleSurfaceNoise(&reference,x,y,z);
                double actual = sampleSurfaceNoise(cached,x,y,z);
                assert(memcmp(&expected,&actual,sizeof(expected)) == 0);
                checks++;
            }
            // Heights still use this sister's full biome seed, never cached biome results.
            applySeed(&generator,DIM_OVERWORLD,seed);
            float expected, actual;
            int x=family%13-6, z=sister*9-13;
            assert(mapApproxHeight(&expected,NULL,&generator,&reference,x,z,1,1)==0);
            assert(mapApproxHeight(&actual,NULL,&generator,cached,x,z,1,1)==0);
            assert(memcmp(&expected,&actual,sizeof(expected)) == 0);
            checks++;
        }
    }
    puts("Surface cache parity passed: 4096 noise samples and 512 biome-dependent heights.");
    return checks == 4608 ? 0 : 1;
}
