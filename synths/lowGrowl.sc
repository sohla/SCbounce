
(
{
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.003], 0, SinOsc.ar(45*MouseX.kr(1,4),1*in,2));
	LocalOut.ar(sig);
	sig
}.play
)


(
{
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.003], 0, LFNoise2.ar(3,1*in,2));
	LocalOut.ar(sig);
	sig
}.play
)


(
{
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.003], 0, SinOsc.ar(1,0,in*2,2));
	LocalOut.ar(sig);
	sig
}.play
)


(
{
	var in = LocalIn.ar(2);
	var sig = LFTri.ar(45*[1.0,1.003], 0, 2 + (in * MouseX.kr(0,1).lincurve(0.0,1.0,0.0,2.0,-3)));
	LocalOut.ar(sig);
	sig
}.play
)
2.pow(3)