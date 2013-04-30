AppBundle
=========

Cordova Plugin to refer to files in the bindle with the prefix "app-bundle://" instead of platform specific urls such "file:///android_asset" etc.

##Installation - Cordova  2.7 or later
        cordova plugin add directory-of-the-AppBundle-plugin

##Usage
* To refer to a file in the bundle through an XHR or a script tag
        <script type="text/javascript" src="app-bundle:///direct/somefile.js"></script>
* When settings window location or using an anchor tag
        <a href="app-bundle:///redirect/somefile.html"></a>

## Why direct and redirect?
On iOS, you can access files saved by the app only if the location is the default app specific location. Thus rather than navigating directly to the location with app-bundle:///direct, you should get redirected to the correct location with app-bundle:///redirect
