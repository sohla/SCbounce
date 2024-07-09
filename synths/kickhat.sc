(
SynthDef(\kickhat, {
    |out=0, tempo=2, subFreq=40, subDecay=0.2, hatDecay=0.1, amp=0.5|
    var kick, hat, mix, trig, seq;

    // Trigger for the sequencer
    trig = Impulse.kr(tempo);

    // 16-step sequencer
    seq = Demand.kr(trig, 0, Dseq([1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 1, 0], inf));

    // Sub Kick
    kick = SinOsc.ar(XLine.kr(subFreq*2, subFreq, 0.04)) *
           EnvGen.ar(Env.perc(0.01, subDecay), trig * seq);

    // Noise Hi-hat
    hat = HPF.ar(WhiteNoise.ar, 6000) *
          EnvGen.ar(Env.perc(0.001, hatDecay), trig * Demand.kr(trig, 0, Dseq([0, 1, 1, 1], inf)));

    // Mix and output
    mix = (kick * 0.8) + (hat * 0.5);
    mix = Limiter.ar(mix * amp, 0.95);

    Out.ar(out, mix ! 2);
}).add;
)
// Example usage:
x = Synth(\kickhat, [\tempo, 8, \subFreq, 50, \subDecay, 0.6, \hatDecay, 0.05, \amp, 0.7]);
x.set(\tempo, 8); // change tempo
// x.free; // stop the synth