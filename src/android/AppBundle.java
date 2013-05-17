/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package org.apache.cordova;

import java.util.ArrayList;
import java.util.List;
import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.api.DataResource;
import org.apache.cordova.api.DataResourceContext;
import android.net.Uri;
import android.net.Uri.Builder;
import android.util.Log;

public class AppBundle extends CordovaPlugin {

    // Note the expected behaviour:
    // Top level navigation with url in the reroute map should make the browser redirect
    // Resource requests to url in the reroute map should just return the requested data
    // Note: Recursive replacements are supported

    private static final String LOG_TAG = "AppBundle";
    private static final String APP_BUNDLE_REPLACED = "AppBundleReplaced";

    private static class RouteParams {
        public String matchRegex;
        public String replaceRegex;
        public String replacer;
        public boolean redirectToReplacedUrl;

        public RouteParams(String matchRegex, String replaceRegex, String replacer, boolean redirectToReplacedUrl){
            this.matchRegex = matchRegex;
            this.replaceRegex = replaceRegex;
            this.replacer = replacer;
            this.redirectToReplacedUrl = redirectToReplacedUrl;
        }
    }

    private final String BUNDLE_PATH = "file:///android_asset/www/";
    // Have a default replacement path that redirects app-bundle: uri's to the bundle
    // Note: We need to special case app-bundle: uri's during redirection
    // For example lets say the we have the rerouteParams set up as
    //      1) app-bundle:///blah -> file:///android_asset/www/blah
    //      2) file:///android_asset/www/blah -> file:///storage/www/blah
    // We would have app-bundle pointing to a location that is not the bundle due to recursive replacements
    // This is NOT the desired behavior
    private final RouteParams appBundleParams = new RouteParams("^app-bundle:///.*", "^app-bundle:///", BUNDLE_PATH, true);
    private List<RouteParams> rerouteParams = new ArrayList<RouteParams>();

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        resetMap();
    }
    private void resetMap(){
        rerouteParams.clear();
        rerouteParams.add(appBundleParams);
    }

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) {
        if ("addAlias".equals(action)) {
            addAlias(args, callbackContext);
            return true;
        } else if ("clearAllAliases".equals(action)) {
            clearAllAliases(args, callbackContext);
            return true;
        }
        return false;
    }

    private void addAlias(CordovaArgs args, CallbackContext callbackContext) {
        try {
            String sourceUrlMatchRegex = args.getString(0).replace("{BUNDLE_WWW}", getRegex(BUNDLE_PATH));
            String sourceUrlReplaceRegex = args.getString(1).replace("{BUNDLE_WWW}", getRegex(BUNDLE_PATH));
            String replaceString = args.getString(2).replace("{BUNDLE_WWW}", BUNDLE_PATH);
            boolean redirectToReplacedUrl = args.getBoolean(3);
            if(replaceString.matches(sourceUrlMatchRegex)){
                callbackContext.error("The replaceString cannot match the match regex. This would lead to recursive replacements.");
            } else {
                rerouteParams.add(new RouteParams(sourceUrlMatchRegex, sourceUrlReplaceRegex, replaceString, redirectToReplacedUrl));
                callbackContext.success();
            }
        } catch(Exception e) {
            callbackContext.error("Could not add alias");
            Log.e(LOG_TAG, "Could not add alias");
        }
    }

    private void clearAllAliases(CordovaArgs args, CallbackContext callbackContext) {
        try {
            resetMap();
            callbackContext.success();
        } catch(Exception e) {
            callbackContext.error("Could not clear aliases");
            Log.e(LOG_TAG, "Could not clear aliases");
        }
    }

    private String getRegex(String string){
        return string.replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("^", "\\^")
            .replace("$", "\\$")
            .replace(".", "\\.")
            .replace("|", "\\|")
            .replace("?", "\\?")
            .replace("*", "\\*")
            .replace("+", "\\+")
            .replace("(", "\\(")
            .replace(")", "\\)");
    }

    private RouteParams getChosenParams(String uri){
        if(uri == null) {
            return null;
        } else {
            uri = Uri.parse(uri).toString();
        }
        for(int i = rerouteParams.size() - 1; i >= 0; i--){
            RouteParams param = rerouteParams.get(i);
            if(uri.matches(param.matchRegex)){
                return param;
            }
        }
        return null;
    }

    @Override
    public Object onMessage(String id, Object data) {
        if("onPageStarted".equals(id)){
            String url = data == null? null: data.toString();
            RouteParams params = getChosenParams(url);
            if(params != null && params.redirectToReplacedUrl) {
                Uri uri = Uri.parse(url);
                if(uri.getQueryParameter(APP_BUNDLE_REPLACED) == null){
                    // Top Level Navigation, redirect correctly
                    String newPath = url.replaceAll(params.replaceRegex, params.replacer);
                    //We need to special case app-bundle: uri's
                    if(params.equals(appBundleParams)){
                        Builder builder = Uri.parse(newPath).buildUpon();
                        builder.appendQueryParameter(APP_BUNDLE_REPLACED, "true");
                        newPath = builder.build().toString();
                    }
                    webView.stopLoading();
                    webView.loadUrl(newPath);
                }
            }
        }
        return null;
    }

    @Override
    public DataResource handleDataResourceRequest(DataResource dataResource, DataResourceContext dataResourceContext) {
        DataResource ret = null;
        if(!dataResourceContext.getDataMap().containsKey(APP_BUNDLE_REPLACED)){
            String uri = dataResource.getUri().toString();
            RouteParams params = getChosenParams(uri);
            if(params != null){
                // Just send data as we can't tell if this is top level or not.
                // If this is a top level request, it will get trapped in the onPageStarted event handled above.
                String newUri = uri.replaceAll(params.replaceRegex, params.replacer);
                ret = new DataResource(cordova, Uri.parse(newUri));
                // We need to special case app-bundle: uri's
                if(params.equals(appBundleParams)){
                    dataResourceContext.getDataMap().put(APP_BUNDLE_REPLACED, "");
                }
            }
        }
        return ret;
    }
}