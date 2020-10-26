(
SynthDef("tester", {|id|
	var imp = Impulse.kr(10);
	SendReply.kr(imp, '/level', 1234, id)}
).add;
)


(
Array.fill(8, {|i|
	var synth, osc;
	synth = Synth("tester", [\number, i], addAction: 'addToHead');
	osc = OSCFunc({|msg| msg.postln}, '/vol', s.addr, argTemplate: [ 4444+i ]);
});

)




a = Synth(\tester, [\id, 0], addAction: 'addToHead');
a.set(\id, 999);
s.freeAll;
a.free;

o = OSCFunc({|msg, time, addr, recvPort|[msg, time, addr, recvPort].postln}, '/level/data');
o = OSCFunc({|msg| msg.postln}, '/level', argTemplate: [ a.nodeID, 999 ]);
o.free;

p = OSCdef( \test, { |msg| msg.postln }, "/level");
p = OSCdef( \test, { |msg| msg.postln }, "/level", argTemplate: [ a.nodeID ] );
p.free;



x = Synth(\default);
OSCFunc({ 'ended!'.postln }, '/n_end', s.addr, nil, [x.nodeID]).oneShot;
x.release();


