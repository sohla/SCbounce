echo "includePaths:
    - overwrites
    - SCClassLibrary
excludePaths:
    - $HOME/Library/Application Support/SuperCollider/Extensions
    - /Library/Application Support/SuperCollider/Extensions
    - /Applications/SuperCollider3.9.1/SuperCollider.app/Contents/Resources/SCClassLibrary
postInlineWarnings: true" > langconf.yaml;
./MacOS/sclang -a -l langconf.yaml init.scd
