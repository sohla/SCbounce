(
	var a = 99;
	var startup;
	var dataPath = "~/Develop/SuperCollider/Projects/SCbounce/data/";

	["Hello World",a].postln;


		startup = {

			DataCollector.makeBackupFolder();
			DataCollector.folder = PathName.new(dataPath).fullPath;

		};

)
