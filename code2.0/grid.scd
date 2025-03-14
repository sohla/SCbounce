var spacing = 2;

var makeButton = {|d|
	UserView()
	.background_(Color.black.alpha_(0.8))
	.mouseDownAction_({|m|m.backColor_(Color.new255(0, 139, 69))})
	.mouseUpAction_({|m|m.backColor_(Color.black.alpha_(0.8))})
	.drawFunc_({Pen.stringAtPoint(d, 10@10, Font(size:30), Color.new255(255, 139, 69))})
	.animate_(true)
	.frameRate_(60)
};

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

grid;