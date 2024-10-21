(
Ndef(\interactiveWind, {
    |out=0, amp=0.5, density=0.5, strength=0.5,
     filterFreq=1000, filterQ=0.5,
     reverbMix=0.5, reverbRoom=0.5, reverbDamp=0.2|

    var wind, filtered, reverbed;
    var densityMod, strengthMod;

    // Modulate density and strength
    densityMod = LFNoise2.kr(0.1).range(0.8, 1.2) * MouseX.kr(0, 1);
    strengthMod = LFNoise2.kr(0.2).range(0.8, 1.2) * MouseY.kr(0, 1);

    // Base wind sound
	wind = [WhiteNoise.ar(1),WhiteNoise.ar(1)];

    // Apply density control (affects graininess of the wind)
    wind = Dust.ar(densityMod * 10000) * wind;

    // Apply strength control (affects amplitude and filter)
    wind = wind * strengthMod;

    // Apply resonant filter
	filtered = RLPF.ar(wind, filterFreq  * strengthMod.linexp(0, 1, 0.5, 2), filterQ);

    // Apply reverb
    reverbed = FreeVerb.ar(filtered, reverbMix, reverbRoom, reverbDamp);

    Out.ar(out, reverbed * amp);
}).play;
)
