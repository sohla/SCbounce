HID.findAvailable;


HID.postAvailable;


// HID.debug = true;

HIDFunc.trace( true );

~myhid = HID.open( 121, 1 );


HID.openAt( 3 );

HID.closeAll;

HIDdef.usage( \example, { |...args| args.postln; }, \X, \Mouse );


HIDdef.usageID( \example2, { |...args| args.postln; }, 9, 9 );

HIDdef( \example2 ).free;


