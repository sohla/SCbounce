// Define control buses
(
~paramBus = Bus.control(s, 3);  // 3 channels for freq, amp, and cutoff

// Define a SynthDef
SynthDef(\paramChangingSynth, {
    |paramBus, gate=1|
    var sig, env, freq, amp, cutoff;
    #freq, amp, cutoff = In.kr(paramBus, 3);  // Read all 3 parameters from the bus
    sig = Saw.ar(freq);
    sig = LPF.ar(sig, cutoff);
    env = EnvGen.kr(Env.adsr(0.01, 0.3, 0.5, 0.1), gate, doneAction: 2);
    Out.ar(0, Pan2.ar(sig * env * amp));
}).add;
)

(
// Define a pattern for note events
~pattern = Pbind(
    \instrument, \paramChangingSynth,
    \paramBus, ~paramBus.index,
    \dur, 0.25,
    \freq, Pseq([440, 330, 550, 660], inf),
    \amp, Pwhite(0.3, 0.7),
    \cutoff, Pwhite(500, 2000),
	// \updateBus, Pfunc { |ev|
	// 	~paramBus.setn([ev[\freq], ev[\amp], ev[\cutoff]]);
	// }
);

)

(// Function to update control buses
~updateBuses = { |freq, amp, cutoff|
    ~paramBus.setn([freq, amp, cutoff]);
};
)

// Start
(
~player = ~pattern.play;
~updateBuses.(200,0.2,1500);

~routine = Routine{
	loop{
		~updateBuses.(200,0.2,200.rrand(2000));
		0.1.yield;
	}
}.play;
)
// Clean up
(
	~player.stop;
    ~freqBus.free;
    ~ampBus.free;
    ~cutoffBus.free;
);
