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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.FileHelper;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceResponse;

public class AppBundle extends CordovaPlugin {

     // Note the expected behaviour:
     // Top level navigation with url in the reroute map should make the browser redirect
     // Resource requests to url in the reroute map should just return the requested data

    private final String LOG_TAG = "AppBundle";

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

    private final RouteParams appBundleParams = new RouteParams("^app-bundle:///.*", "^app-bundle:///", "file:///android_asset/www/", true);
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
            String sourceUrlMatchRegex = args.getString(0);
            String sourceUrlReplaceRegex = args.getString(1);
            String replaceString = args.getString(2);
            boolean redirectToReplacedUrl = args.getBoolean(3);
            if(replaceString.matches(sourceUrlMatchRegex)){
                callbackContext.error("The replaceString cannot match the match regex. This would lead to recursive replacements.");
            } else {
                rerouteParams.add(new RouteParams(sourceUrlReplaceRegex, sourceUrlReplaceRegex, replaceString, redirectToReplacedUrl));
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

    private WebResourceResponse getWebResourceResponseForFile(String file) {
        String mimetype = FileHelper.getMimeType(file, this.cordova);
        String encoding = null;
        if (mimetype != null && mimetype.startsWith("text/")) {
            encoding = "UTF-8";
        }

        InputStream is = null;

        if(file.startsWith("file:///android_asset/")) {
            try {
                String filePath = file.substring("file:///android_asset/".length());
                filePath = Uri.parse(filePath).getPath();
                is = this.cordova.getActivity().getAssets().open(filePath);
            } catch (IOException ioe) {
                Log.e(LOG_TAG, "Rerouting error: Error reading asset " + file);
            }
        } else {
            String[] parts = file.split("file://", 2);
            if(parts.length == 2) {
                String filePath = parts[1];
                filePath = Uri.parse(filePath).getPath();
                try {
                    is = new FileInputStream(filePath);
                } catch (FileNotFoundException e) {
                    Log.e(LOG_TAG, "Rerouting error: Error reading file " + file);
                }
            }
        }

        if(is == null) {
            return null;
        } else {
            return new WebResourceResponse(mimetype, encoding, is);
        }
    }

    private RouteParams getChosenParams(String uri){
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
            String url = data == null? "":data.toString();
            RouteParams params = getChosenParams(url);
            if(params != null && params.redirectToReplacedUrl) {
                // Top Level Navigation, redirect correctly
                String newPath = url.replaceAll(params.replaceRegex, params.replacer);
                webView.stopLoading();
                webView.loadUrl(newPath);
            }
        }
        return null;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(String url) {
        RouteParams params = getChosenParams(url);
        if(params != null){
            // Just send data as we can't tell if this is top level or not.
            // If this is a top level request, it will get trapped in the onPageStarted event handled above.
            String newPath = url.replaceAll(params.replaceRegex, params.replacer);
            return getWebResourceResponseForFile(newPath);
        }
        return null;
    }
}
