w = Window.new.front;

v = Stethoscope(s,2, view:w.view);
v.view.bounds = Rect(0,0,600,300);
v.rate = \control;
v.bus.value = 0.2
v.cycle = 4096 * 8
v.xZoom = 4



SynthDef(\test, {|bus=0,freq=111, amp=0.1|
    var sig = LFTri.kr(freq * MouseX.kr(1,100), amp);
    Out.kr(0, sig);
}).add;


a = Synth(\test, [\bus, v.bus, \freq, 1, \amp, 0.1]);

a.free


s.freeAll



y = [ 1, 2, 3 ];

y.reshape(3,1)
y
y = y.add(4)
y.removeAt(0)
y



Array.with([Array.newClear(3),Array.newClear(3),Array.newClear(3)])


Array.fill(10,[0.1,0.2,0.4]);
a = [Array.fill(10,0.8),Array.fill(10,0.2),Array.fill(10,0.3)];
a[0].add(0.1)