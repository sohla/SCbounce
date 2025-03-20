Env.new(levels: [0, 1, 0.9, 0], times: [0.1, 0.5, 1], curve: [-5, 0, -5]).plot;


Env(Signal.sineFill(100, 1.0/[1, 2, 3, 4, 5, 6])).plot
Env(levels:Signal.sineFill(100, 1.0/[2, 0.1,0.1]),times:[0.01]).plot


Env([0, 1], [1], \lin).plot;

Env([0, 1], [1], -3).plot;

Env([0, 1], [1], \sine).plot; // ease in out

Env([0, 1.2, 0.8, 1.1, 0.9, 1.03, 0.97, 1].reverse, [0.3, 0.1, 0.1, 0.1, 0.1, 0.1, 0.2].reverse, [\sine, \sine, \sine, \sine, \sine, \sine, \sine]).plot; //spring

Env([1,0.001]*1.neg, [1], \exp).plot

Env([0, -0.1, 1], [0.2, 0.8], [\sine, 3]).plot; //overshoot
Env([0, -0.2, 1], [0.3, 0.7], [\sine, \sin]).plot; // anticipate
Env([0, 0.2, 0.2, 0.4, 0.4, 0.6, 0.6, 0.8, 0.8, 1], [0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1], [\step, \step, \step, \step, \step, \step, \step, \step, \step]).plot;//step

Env([0, 1.1, 0.9, 1.03, 0.97, 1], [0.5, 0.1, 0.1, 0.1, 0.2], [\sine, \sine, \sine, \sine, \sine]).plot; //bounce

Env([0, 1], [1], \circleIn).plot;


// custom envelopes using Signal 
(
var sig = Signal.newClear(100);
sig.waveFill({ |x| x }, 0, 1);
Env.new(sig, 0.01).plot;
) //using a signal


(
var sig = Signal.newClear(100);
sig.waveFill({ |x|
  1 - sqrt(1 - x.squared)
}, 0, 1);
Env.new(sig, 0.01).plot;
) //circle

(
var sig = Signal.newClear(100);
sig.waveFill({ |x|
  var freq = 10; // frequency of oscillation
  var decay = 3; // rate of decay
  0.5 + (0.5 * sin(freq * x * pi) * exp(decay.neg * x))
}, 0, 1);
Env.new(sig, 0.01).plot;
)//ripple



(
var sig = Signal.newClear(100);
sig.waveFill({ |x|
  var pulse1 = exp(-200 * (x - 0.2).squared) * 0.8;
  var pulse2 = exp(-200 * (x - 0.4).squared) * 0.8;
  var baseline = 0.2;
  baseline + pulse1 + pulse2
}, 0, 1);
Env.new(sig, 0.01).plot;
)//heart beat


(
var sig = Signal.newClear(100);
sig.waveFill({ |x|
  var cycles = 5; // number of growth cycles
  var baseGrowth = x; // underlying trend
  var seasonalEffect = 0.05 * sin(cycles * 2pi * x); // seasonal variation
  baseGrowth + seasonalEffect
}, 0, 1);
Env.new(sig, 0.01).plot;
)//wobble


(
var sig = Signal.newClear(100);
sig.waveFill({ |x|
  var baseWind = 0.2 + (0.3 * x); // gradually increasing base wind
  var gust = 0.5 * exp(-30 * (x - 0.7).squared); // sudden gust
  baseWind + gust
}, 0, 1);
Env.new(sig, 0.01).plot;
)//gust

(
var sig = Signal.newClear(100);
sig.waveFill({ |x|
  var flutterFreq = 20;
  var hoverPoint1 = exp(-50 * (x - 0.3).squared);
  var hoverPoint2 = exp(-50 * (x - 0.7).squared);
  var flutter = 0.1 * sin(flutterFreq * pi * x);
  (0.5 * x) + (0.5 * (x.sqrt)) + flutter - (0.2 * hoverPoint1) - (0.2 * hoverPoint2)
}, 1, 0);
Env.new(sig, 0.01).plot;
)//butterfly

(
var sig = Signal.newClear(100);
sig.waveFill({ |x|
  var pWave = 0.3 * exp(-30 * (x - 0.2).squared) * sin(40 * pi * x);
  var sWave = 0.7 * exp(-15 * (x - 0.5).squared) * sin(20 * pi * x);
  0.5 + pWave + sWave
}, 0, 1);
Env.new(sig, 0.01).plot;
)//earth quake




