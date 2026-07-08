(
var w = 400, h = 300;
var window = Window("human1", Rect(200, 200, w, h)).front;
var canvas;

var headCy = -0.6, headR = 0.15;
var neckY = -0.45;
var shoulderY = -0.45;
var hipY = 0.1;
var footY = 0.65;
var armSpan = 0.3;
var legSpread = 0.15;

var headSegs = 32;

var head = Canvas3DItem()
	.color_(Color.black)
	.width_(1)
	.fill_(true)
	.paths_([
		(headSegs + 1).collect {|i|
			var a = i / headSegs * 2pi;
			[sin(a) * headR, headCy + (cos(a) * headR), 0]
		}
	]);

var torso = Canvas3DItem()
	.color_(Color.black)
	.width_(32)
	.paths_([ [[0, neckY+0.1, 0], [0, hipY, 0]] ]);

var arms = Canvas3DItem()
	.color_(Color.black)
	.width_(16)
	.paths_([ [[armSpan.neg, hipY, 0], [0, shoulderY, 0], [armSpan, hipY, 0]] ]);

var legs = Canvas3DItem()
	.color_(Color.black)
	.width_(16)
	.paths_([ [[legSpread.neg, footY, 0], [0, hipY, 0], [legSpread, footY, 0]] ]);

window.background = Color.white;

canvas = Canvas3D(window, window.view.bounds)
	.background_(Color.white)
	.scale_(250)
	.perspective_(0.4)
	.distance_(2)
	.add(head)
	.add(torso)
	.add(arms)
	.add(legs);

canvas.animate(40, {|t|
	canvas.transforms = [
		Canvas3D.mRotateX(t * 0.013 % 2pi),
		Canvas3D.mRotateY(t * 0.023 % 2pi),
		Canvas3D.mRotateZ(t * 0.007 % 2pi)
	];
});

window.onClose = { canvas.animate(nil) };
)
