
    // create a new DataCollector:
d = DataCollector.new( "test" );
d.collection; // empty sorted list

~date = d.addData( [1,2,3] );
d.collection // now we have one entry with data in it and a timestamp

d.addMetaData( [5,6,7], ~date ); // add some metadata to it
d.collection

d.addData( [3,4.5,6] );
d.collection

// create a backup:
d.backup

d.addData( [99,100] );

d.collection

// recover from the backup
d.recover

d.lastBackupPath;



// create a new collector:
e = DataCollector.new( "test2" );

// recover from the backup
e.recover

// we have the same data again:
e.collection;

(
File.saveDialog("save this as..","fuk yes", { |path|
	path.postln;

},{

});
)


