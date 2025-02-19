var spacing = 2;

var func = {|v|
	Pen.fillColor = Color.grey(0.0,  0.09);
	Pen.addRect(Window.screenBounds);
	Pen.fill;

    Pen.use{
        10.do{
            Color.hsv(rrand(0.0, 0.999), rrand(0.2, 0.7), 1, 1).set;
			Pen.addArc((Window.screenBounds.width.rand)@(Window.screenBounds.height.rand), 5.exprand(150), 2pi.rand, pi);
            Pen.perform([\stroke, \fill].choose);
        }
    }
};

var visual = {
	UserView()
	.background_(Color.black.alpha_(0.8))
	.drawFunc_(func)
	.clearOnRefresh_(false)
	.animate_(true)
	.frameRate_(60)
};

var visuals = {
	View().layout_(GridLayout.rows(
		[
			visual.(),
			visual.(),
		],
		[
			visual.(),
			visual.(),
		],
).hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]))
};

visuals;