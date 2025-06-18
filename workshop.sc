
// -----------------------------------------------------------------
// SCLang Commands
// -----------------------------------------------------------------

// shift + return (evaluate)

"Hello World".postln

{ SinOsc.ar(440.0, 0.0, 0.3) }.play

// command + . (stop)

// command + d (documentation)

SinOsc

{ SinOsc.ar(200, 0, 0.5) }.play;


// -----------------------------------------------------------------
// Scope + Variables
// -----------------------------------------------------------------

// can be lazy ( but dont use s!)
a = 1
b = 7
a+b

(
var freq = 111.0;
{ SinOsc.ar(freq, 0.0, 0.3) }.play;
)


(
var a = 1;
a.postln;
a = a + 1;
a.postln;
)


// ...but why

// urgh this looks ugly!
{ SinOsc.ar( MouseX.kr(100,400, 1), 0.0, LFTri.ar( MouseY.kr(1,40)).range(0.0,0.3))!2 }.play


// this looks better
({
	var lfo = LFTri.ar( MouseY.kr(1,40)).range(0.0,0.3);
	var freq = MouseX.kr(100,400, 1);
	var sig = SinOsc.ar( freq, 0, lfo);
	sig!2

}.play
)


// -----------------------------------------------------------------
// Comments
// -----------------------------------------------------------------

/*
More...
Comments
*/


// -----------------------------------------------------------------
// Quick intro into Arrays
// -----------------------------------------------------------------
a = [1,2,3,4]
a[3]
a.at(3)
a.put(3,5)
a.reverse

b = [2,2]

c = 2
c!2

// -----------------------------------------------------------------
// Channels as an Array ( left and right )
// -----------------------------------------------------------------

{ SinOsc.ar( 111, 0.0, 0.3) }.play // left channel

{ SinOsc.ar( [111,111], 0.0, 0.3) }.play

{ SinOsc.ar( 111!2, 0.0, 0.3) }.play // both channels

{ SinOsc.ar( 111, 0.0, 0.3)!2 }.play // also both channels

{ SinOsc.ar( 111, 0.0, [0.1, 0.3]) }.play // more on the right

{ SinOsc.ar( 281, [0, MouseX.kr(0,pi)], 0.3) }.scope // move the phase


// -----------------------------------------------------------------
// Use the Mouse
// -----------------------------------------------------------------

{ SinOsc.ar( MouseX.kr(100,400), 0.0, 0.3)!2 }.play // linear

{ SinOsc.ar( MouseX.kr(100,400, 1), 0.0, 0.3)!2 }.play //exponential


// -----------------------------------------------------------------
// Debug
// -----------------------------------------------------------------

// poll
({
	var lfo = LFTri.ar( MouseY.kr(1,40).poll);
	var sig = SinOsc.ar( MouseX.kr(100,400, 1), 0, lfo.range(0.0,0.3));
	sig!2

}.play
)

// scope
({
	var lfo = LFTri.ar( MouseY.kr(1,40)).scope;
	var sig = SinOsc.ar( MouseX.kr(100,400, 1), 0, lfo.range(0.0,0.3));
	sig!2

}.play
)

// more than 1 scope
({
	var lfo = LFTri.ar( MouseY.kr(1,40)).scope;
	var sig = SinOsc.ar( MouseX.kr(100,400, 1), 0, lfo.range(0.0,0.3)).scope;
	sig!2

}.play
)

// -----------------------------------------------------------------
// Tips
// -----------------------------------------------------------------

{ LFNoise0.ar(440, 0.3) }.scope // carefull!!!
{ LFNoise1.ar(440, 0.3) }.scope
{ LFNoise2.ar(440, 0.3) }.scope

{ SinOsc.ar(LFNoise0.ar(8).range(220, 440), 0.0, 0.3) }.play
{ SinOsc.ar(LFNoise1.ar(8).range(220, 440), 0.0, 0.3) }.play

// urgh! i don't like working in freq and amps

69.midicps // midi to cycles per second
-10.dbamp // db to amp

440.cpsmidi
0.3.ampdb

{ SinOsc.ar(69.midicps, 0.0, -10.dbamp)!2 }.play


// -----------------------------------------------------------------
// Explore generators and filters
// -----------------------------------------------------------------

{ Dust.ar(8)!2 }.play
{ SinOsc.ar(LFNoise1.ar(1).range(100,800), 0.0, 0.3)!2 }.play
{ RLPF.ar( WhiteNoise.ar(0.3), 200, 0.02)!2 }.play

// put them together
({
	var src = Dust.ar(8);
	var filter_freq = LFNoise1.ar(1).range(100,800);
	var rq = 0.02;
	var sig = RLPF.ar(src, filter_freq, rq);
	sig!2

}. play;
)


// add some generators together and add a filter
({
	var freq = MouseY.kr(50,62).round.midicps;
	var osc1 = Saw.ar([freq, freq * 1.004]);
	var osc2 = SinOsc.ar([freq-1, freq -1 * 0.005]);
	var osc3 = LFTri.ar([freq+1, freq * 1.004]);
	var src = (osc1 + osc2 + osc3) / 3;
	var filterFreq = MouseX.kr(500,5000,1);
	var sig = RLPF.ar(src, filterFreq, 0.1, 0.3);

	sig // we already have 2 channels

}. play;
)

// -----------------------------------------------------------------
// Let's make some sounds
// -----------------------------------------------------------------

// use the Help browser to explore....

// Generators
SinOsc
LFTri
LFSaw
Pulse
VarSaw

// Filters
RLPF
BPF
MidEQ
LPF
HPF

// User Interaction
MouseX
MouseY
MouseButton
















// Functions
(
var func = { |val| val * 10 };
var freq = func.(11);
var sig = { SinOsc.ar(freq, 0.0, 0.3) };
sig.play;
)
