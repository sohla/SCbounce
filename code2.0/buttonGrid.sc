(
var func = {|v|
	Pen.fillColor = Color.grey(0.0,  0.05);
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

var makeButton = {|d|
	UserView()
	.background_(Color.black.alpha_(0.8))
	.mouseDownAction_({|m|m.backColor_(Color.new255(0, 139, 69))})
	.mouseUpAction_({|m|m.backColor_(Color.black.alpha_(0.8))})
	.drawFunc_({Pen.stringAtPoint(d, 10@10, Font(size:30), Color.new255(0, 139, 69))})
	.animate_(true)
	.frameRate_(60)
};

var spacing = 2;

var grid = {
	View().layout_(GridLayout.rows(
		[
			makeButton.("left arm"),
			makeButton.("right arm"),
		],
		[
			makeButton.("left leg"),
			makeButton.("right leg"),
		],
).hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]))
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


var device = {|d|
	UserView()
		.background_(Color.black.alpha_(0.8))
		.drawFunc_({
		Pen.stringAtPoint(d, 10@10, Font(size:30), Color.new255(0, 139, 69))
	})
};

var devices = {
	View().layout_(GridLayout.rows(
		[
			device.("device A"),
			device.("device B"),
		],
		[
			device.("device C"),
			device.("device D"),
		],
	).hSpacing_(spacing).vSpacing_(spacing).margins_([spacing,spacing,spacing,spacing]))
};


var stack =
	StackLayout(
		grid.(),
		devices.(),
		visuals.(),
	);


var tabButton = {|title|
	Button()
	.minHeight_(100)
	.font_(Font(size:30))
	.states_([
		[title, Color.white, Color.grey],
		[title, Color.white, Color.new255(0, 139, 69)]
	])
	.mouseDownAction_({|but|
		if(but.value == 0,{
			but.parent.children.do({|ab,i|
				if(but != ab, {
					ab.valueAction_(0);
				},{
					i.postln;
					stack.index = i;
				});
			});
		},{but.valueAction_(0)});
	})
};


var tab1;
var tabs = {|t|
	View().layout_(HLayout(
		tab1 = tabButton.("🁪"),
		tabButton.("⚃"),
		tabButton.("※")
	).spacing_(0).margins_(0)).maxHeight_(100);

};


w = Window().bounds_(Rect(100,100,1000,700)).layout_(
	VLayout(
		tabs.(),
		stack.()

	);
).front;
tab1.valueAction = 1;
)