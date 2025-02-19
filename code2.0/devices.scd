var spacing = 2;

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
devices;