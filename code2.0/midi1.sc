(
p = Pbind(
    \dur, Prand([1], inf),
	\note, Pseq([0,2,4,5,7,9,11], 1),
    \db, Pwhite( -10,-4 )
);

m = SimpleMIDIFile( "~/Desktop/Cmaj.mid" );
m.init1( 2, 120, "4/4" );
m.fromPattern( p );
)

m.plot
m.p.play
m.write

(
SynthDef( "organ", { |freq = 440, sustain = 1, amp = 0.1|
        var sig;
        sig = LFPar.ar( freq * [1,2,3,5], 0, amp/[2,4,5,7] );
        Out.ar( 0, Env([0,1,1,0], [0.025,sustain,0.025]).kr(2) * sig.dup );
}).add;
);
n = SimpleMIDIFile.read( "~/Desktop/Cmaj.mid" );
n = SimpleMIDIFile.read( "~/Downloads/1_Cmaj.mid" );
n.p.play