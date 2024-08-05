(
SynthDef(\swampAmbience, {
    arg out=0, amp=1,
        windAmp=0.3, windSpeed=0.1,
        rainAmp=0.2, rainDensity=0.7,
        frogAmp=0.2, frogDensity=0.5,
        cricketAmp=0.15, cricketDensity=0.8,
        birdAmp=0.1, birdDensity=0.3,
        waterAmp=0.25;

    var wind, rain, frog, cricket, bird, water, mix;

    // Wind
    wind = {
        var base = LPF.ar(PinkNoise.ar, 400);
        var whistle = HPF.ar(WhiteNoise.ar, 2000);
        var windNoise = XFade2.ar(base, whistle, SinOsc.kr(windSpeed).range(-0.5, 0.5));
        windNoise * LFNoise2.kr(0.1).exprange(0.7, 1)
    } ! 2;

    // Rain
    rain = {
        var drops = Dust.ar(rainDensity * 100);
        var droplets = Ringz.ar(drops, TExpRand.ar(200, 1000, drops), 0.01);
        LPF.ar(droplets, 3000)
    } ! 2;

    // Frog
    frog = {
        var trig = Dust.kr(frogDensity);
        var freq = TChoose.kr(trig, [300, 400, 500, 600]);
        var env = EnvGen.ar(Env.perc(0.01, 0.2), trig);
        SinOsc.ar(XLine.ar(freq*2, freq, 0.02)) * env
    } ! 2;

    // Cricket
    cricket = {
        var trig = Impulse.kr(cricketDensity * 10);
        var chirp = LFPulse.ar(4000, 0, LFNoise1.kr(100).range(0.1, 0.5)) *
                    EnvGen.ar(Env.perc(0.01, 0.05), trig);
        HPF.ar(chirp, 3000)
    } ! 2;

    // Bird
    bird = {
        var trig = Dust.kr(birdDensity);
        var freq = TChoose.kr(trig, [2000, 3000, 4000, 5000]);
        var env = EnvGen.ar(Env.perc(0.01, 0.1), trig);
        SinOsc.ar(XLine.ar(freq*1.5, freq, 0.08)) * env
    } ! 2;

    // Water
    water = LPF.ar(BrownNoise.ar, 400) * LFNoise2.kr(0.2).exprange(0.7, 1);

    // Mix all elements
    mix = (wind * windAmp) +
          (rain * rainAmp) +
          (frog * frogAmp) +
          (cricket * cricketAmp) +
          (bird * birdAmp) +
          (water * waterAmp);

    Out.ar(out, mix * amp);
}).add;
)
(
~swamp = Synth(\swampAmbience, [
    \windAmp, 0.3,
    \windSpeed, 0.1,
    \rainAmp, 0.2,
    \rainDensity, 0.7,
    \frogAmp, 0.2,
    \frogDensity, 0.5,
    \cricketAmp, 0.15,
    \cricketDensity, 0.8,
    \birdAmp, 0.1,
    \birdDensity, 0.3,
    \waterAmp, 0.25,
    \amp, 0.5
]);

~swamp.set(\rainAmp, 0.5, \rainDensity, 1);  // Increase rain
~swamp.set(\windSpeed, 0.3, \windAmp, 0.6);  // Make wind stronger and faster
~swamp.set(\frogDensity, 0.8, \frogAmp, 0.9);  // More frequent and louder frogs

~swampModulation = Task({
    loop {
        ~swamp.set(\rainAmp, rrand(0.1, 0.5));
        ~swamp.set(\windSpeed, rrand(0.01, 0.3));
        ~swamp.set(\birdDensity, rrand(0.6, 0.9));
        5.wait;
    }
}).play;
)