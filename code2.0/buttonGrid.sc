(
var func = {|me|
	var d = Date.getDate.rawSeconds.asInteger.postln;
    Pen.use{
        10.do{
            Color.red(rrand(0.0, 1), rrand(0.0, 0.5)).set;
            Pen.addArc((400.exprand(2))@(200.rand), rrand(10, 200), 2pi.rand, pi);
            Pen.perform([\stroke, \fill].choose);
        }
    }
};

b = {|f|
	UserView()
	.background_(Color.black.alpha_(0.8))
	.mouseDownAction_({|m|f.(); m.backColor_(Color.green)})
	.mouseUpAction_({|m|f.(); m.backColor_(Color.black.alpha_(0.8))})
	.drawFunc_(func)
	.animate_(true)
	.frameRate_(60)
};
a = 4;
w=Window().layout_( GridLayout.rows(
	[
		b.({"do 0".postln}),
		b.({"do 1".postln}),
		b.({"do 2".postln}),
		b.({"do 2".postln}),
	],
	[
		b.({"do 3".postln}),
		b.({"do 4".postln}),
		b.({"do 5".postln}),
		b.({"do 2".postln}),
	],

).hSpacing_(a).vSpacing_(a).margins_([a,a,a,a])).front;
)


