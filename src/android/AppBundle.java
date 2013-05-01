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

import java.io.IOException;
import java.io.InputStream;

import org.apache.cordova.api.CordovaPlugin;
import org.apache.cordova.FileHelper;

import android.net.Uri;
import android.webkit.WebResourceResponse;

public class AppBundle extends CordovaPlugin {

     // Note the expected behaviour:
     // Top level navigation with url app-bundle:///fileInBundle should make the browser redirect to file:///android_asset/www/fileInBundle
     // Resource requests to app-bundle:///fileInBundle should just return the requested data

    private final String urlPrefix = "app-bundle:///";

    private WebResourceResponse getWebResourceResponseForFile(String assetFile) {
        String mimetype = FileHelper.getMimeType(assetFile, this.cordova);
        String encoding = null;
        if (mimetype != null && mimetype.startsWith("text/")) {
            encoding = "UTF-8";
        }

        InputStream is = null;
        try {
            String filePath = Uri.parse(assetFile).getPath();
            is = this.cordova.getActivity().getAssets().open("www/" + filePath);
        } catch (IOException ioe) {
            return null;
        }

        return new WebResourceResponse(mimetype, encoding, is);
    }

    @Override
    public Object onMessage(String id, Object data) {
        if("onPageStarted".equals(id)){
            // Top Level Navigation, redirect correctly
            String url = data == null? "":data.toString();
            if(url.startsWith(urlPrefix)){
                webView.stopLoading();
                String path = url.substring(urlPrefix.length());
                webView.loadUrl("file:///android_asset/www/" + path);
            }
        }
        return null;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(String url) {
        // Just send data as we can't tell if this is top level or not.
        // If this is a top level request, it will get trapped in the onPageStarted event handled above.
        String path = url.substring(urlPrefix.length());
        return getWebResourceResponseForFile(path);
    }
}
