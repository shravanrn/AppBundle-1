AppBundle
=========

Cordova Plugin to refer to files in the bindle with the prefix "app-bundle://" instead of platform specific urls such "file:///android_asset" etc.
This plugin also allows aliasing of urls to file urls. For example, you can set "file:///backToMain" to alias to "file://storage_card/main.html" etc.

##Installation - Cordova  2.7 or later
        cordova plugin add directory-of-the-AppBundle-plugin

##Usage
* To refer to a file in the bundle
        app-bundle:///somefile.someext
* To alias a path
        appBundle.addAlias("file:///backToMain", "file://storage_card/main.html", function(succeded){
            if(succeded){
                alert("Done");
            }
        });
* To remove the aliases
        appBundle.clearAllAliases(function(succeded){
            if(succeded){
                alert("Done");
            }
        });