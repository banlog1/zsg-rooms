#ifndef ZSG_SURFACE_NOISE_CACHE_H
#define ZSG_SURFACE_NOISE_CACHE_H

#include <stdint.h>
#include "biomenoise.h"

/* Minecraft 1.16.1's Cubiomes surface noise uses Java Random's lower 48 bits.
 * Keep this object in place: SurfaceNoise contains pointers into its own octaves. */
typedef struct {
    SurfaceNoise noise;
    uint64_t lower48;
    int initialized;
} SurfaceNoiseCache;

static SurfaceNoise *surface_noise_for_seed(SurfaceNoiseCache *cache, uint64_t seed) {
    uint64_t lower48 = seed & UINT64_C(0xffffffffffff);
    if (!cache->initialized || cache->lower48 != lower48) {
        initSurfaceNoise(&cache->noise, DIM_OVERWORLD, lower48);
        cache->lower48 = lower48;
        cache->initialized = 1;
    }
    return &cache->noise;
}

#endif
