package com.lyricstage.background

/**
 * AGSL "liquid glass": the background is the artwork's top colour as it comes, with pools
 * of the next top colours drifting behind a slowly undulating glass slab that refracts them.
 * Sung words send ripples through the glass and each new line sweeps a soft band of light
 * across it. Ripples carry their own contrast-pushed colour, so the same ring reads on a
 * black cover exactly as it does on a purple one. Tone mapped so the brightest spots
 * stay well below the lyrics.
 *
 * Coordinates: fragCoord has a top-left origin; p is centred, y down, aspect corrected.
 *
 * Every stage colour stays bounded: pools and ripple rings mix toward the accent colours
 * instead of adding light onto the base, so a light cover can never wash out to white and
 * the brightest thing on screen stays the type. Nothing reaches the tone map below zero
 * either - pow() of a negative is NaN, and a NaN pixel is garbage on some GPUs - so the
 * colour is clamped before the filmic tail runs.
 *
 * A black-and-white cover stays black-and-white: when every palette colour is a grey
 * (as PaletteBuilder keeps a grey artwork), the glass works in luminance alone - no
 * saturation push, no coloured dispersion, no tinted ripple rings. Light still bends;
 * no colour appears that the cover never had.
 */
internal const val LIQUID_GLASS_AGSL = """
uniform float2 uRes;
uniform float uTime;
uniform half3 uC0;
uniform half3 uC1;
uniform half3 uC2;
uniform half3 uBase;
uniform float4 uRip[8];
uniform float uLineT;
uniform float uPools;
uniform float uRipple;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.55;
    for (int i = 0; i < 3; i++) {
        v += a * noise(p);
        p = p * 1.9 + float2(1.7, 9.2);
        a *= 0.45;
    }
    return v / 0.88;
}

// Domain-warped noise picks where each accent shows. Pools are bounded tints toward the
// ripple colours, never added light: on a dark cover the ripples are lighter than the
// base so the pools glow, on a light cover they are darker so the pools shade - yellow
// gets olive waves, light grey gets grey ones - and no cover can blow out to white.
// uBase is the artwork's top colour, used exactly as it comes; the filmic tail at the
// end of main() darkens it into the mid tones the light lyric shade is measured against.
// mono is 1 when every palette colour is a grey: the saturation push is off, so greys
// stay exactly on their own luminance.
half3 lightField(float2 p, float t, half mono) {
    float2 s = p * 0.62;
    float2 q = float2(fbm(s + float2(0.0, t * 0.035)), fbm(s + float2(5.2, -t * 0.03)));
    float2 r = float2(fbm(s + 1.6 * q + float2(1.7, 9.2) + t * 0.022),
                      fbm(s + 1.6 * q + float2(8.3, 2.8) - t * 0.026));
    float f = fbm(s * 0.9 + 1.4 * r);
    float w0 = smoothstep(0.42, 0.82, f);
    float w1 = smoothstep(0.5, 0.88, r.x) * (1.0 - w0 * 0.6);
    float w2 = smoothstep(0.55, 0.92, q.y) * (1.0 - max(w0, w1) * 0.6);
    // uPools scales how far each mix reaches: 0 nearly flat base, 1 standard, 2 vivid
    half pools = half(min(uPools, 2.0));
    half3 col = uBase;
    col = mix(col, uC0, half(min(w0 * 0.85 * pools, 1.0)));
    col = mix(col, uC1, half(min(w1 * 0.70 * pools, 1.0)));
    col = mix(col, uC2, half(min(w2 * 0.55 * pools, 1.0)));
    half lum = dot(col, half3(0.299, 0.587, 0.114));
    half sat = mix(half(1.45), half(1.0), mono);
    return max(mix(half3(lum), col, sat), half3(0.0));
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uRes;
    float aspect = uRes.x / uRes.y;
    float2 p = float2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float t = uTime;

    // the slab is never flat: slow undulation
    float2 slope = float2(
        sin(p.y * 2.3 + t * 0.21) + 0.6 * sin(p.x * 1.7 - p.y * 1.3 + t * 0.17),
        cos(p.x * 2.1 - t * 0.19) + 0.6 * cos(p.y * 1.9 + p.x * 0.8 - t * 0.23)) * 0.012;

    // ripples: a damped ring travelling outwards from each sung word
    float ring = 0.0;
    float glow = 0.0;
    for (int i = 0; i < 8; i++) {
        float4 rp = uRip[i];
        float age = t - rp.z;
        if (rp.w > 0.0 && age >= 0.0 && age < 3.5) {
            float2 c = float2((rp.x - 0.5) * aspect, rp.y - 0.5);
            float2 d = p - c;
            float dist = length(d);
            float x = dist - age * 0.32;
            float env = exp(-x * x * 90.0) * exp(-age * 1.25) * rp.w;
            slope += (d / max(dist, 0.001)) * sin(x * 55.0) * env * 0.035 * uRipple;
            ring += env * uRipple;
            // a wide, soft version of the same ring, for dark glass
            glow += exp(-x * x * 14.0) * exp(-age * 1.4) * rp.w * uRipple;
        }
    }

    // a band of light across the glass on each new line
    float lineAge = t - uLineT;
    float sweep = 0.0;
    if (lineAge > 0.0 && lineAge < 2.2) {
        float s = (p.x * 0.7 - p.y * 0.45) - (lineAge * 1.5 - 1.2);
        sweep = exp(-s * s * 16.0) * (1.0 - lineAge / 2.2);
        slope += float2(0.7, -0.45) * sweep * 0.02;
    }

    // a black-and-white cover stays black-and-white: mono is 1 when every palette colour
    // is a grey (PaletteBuilder keeps a grey artwork grey; the spread over all twelve
    // channel differences also swallows the ±1/255 chroma noise a JPEG greyscale carries)
    float colourFul = 0.0;
    colourFul += abs(uBase.r - uBase.g) + abs(uBase.g - uBase.b) + abs(uBase.b - uBase.r);
    colourFul += abs(uC0.r - uC0.g) + abs(uC0.g - uC0.b) + abs(uC0.b - uC0.r);
    colourFul += abs(uC1.r - uC1.g) + abs(uC1.g - uC1.b) + abs(uC1.b - uC1.r);
    colourFul += abs(uC2.r - uC2.g) + abs(uC2.g - uC2.b) + abs(uC2.b - uC2.r);
    half mono = half(1.0 - smoothstep(0.05, 0.12, colourFul));

    // refraction: the light is seen through the bent glass
    half3 col = lightField(p + slope * 6.0, t, mono);
    // where the glass is dark (black covers) there is nothing bright to refract, and the
    // ring's colour fringes and glints read as hard lines on black; soften them there
    half lum0 = dot(col, half3(0.299, 0.587, 0.114));
    half dark = 1.0 - smoothstep(0.04, 0.3, lum0);
    half edge = mix(1.0, 0.2, dark);
    // the dispersion the bent glass adds, folded onto its own luminance for a grey
    // palette: the light still fringes, but only in brightness
    half3 dispersion = half3(slope.x, 0.3 * (slope.y - slope.x), -slope.y) * 1.6 * edge;
    col += mix(dispersion, half3(dot(dispersion, half3(0.299, 0.587, 0.114))), mono);

    float3 n = normalize(float3(-slope * 14.0, 1.0));
    float3 L = normalize(float3(-0.45, -0.55, 0.7));
    float spec = pow(max(dot(reflect(-L, n), float3(0.0, 0.0, 1.0)), 0.0), 40.0);
    col += half(spec * 0.2) * edge + half(sweep * 0.1);

    // ripples carry their own colour, already pushed apart from the background in
    // lightness: the ring mixes toward it, bounded like the pools - a fine light ring on
    // dark glass, a fine dark ring on light glass, plain luminance when the palette is grey
    half3 rippleTint = mix(uC0, half3(dot(uC0, half3(0.299, 0.587, 0.114))), mono);
    half rw = half(min(ring * 0.55 * (1.0 - dark) + min(glow, 1.5) * 0.30 * dark, 1.0));
    col = mix(col, rippleTint, rw);

    // nothing negative survives to the tone map: pow() of a negative is NaN, and a NaN
    // pixel is garbage on some GPUs
    col = max(col, half3(0.0));

    // vignette, then a filmic roll-off that keeps highlights under the white type
    col *= half(1.0 - 0.6 * smoothstep(0.3, 1.2, length(p * float2(0.85, 1.1))));
    col = col / (1.0 + col * 0.7);
    col = pow(col, half3(1.18)) * 0.95;
    col += half((hash(fragCoord + fract(t) * 100.0) - 0.5) * 0.018);
    return half4(col, 1.0);
}
"""
