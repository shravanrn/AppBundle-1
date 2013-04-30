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
#import "AppBundle.h"

#pragma mark declare

@interface AppBundleURLProtocol : NSURLProtocol
- (void)issueNSURLResponseForFile:file;
- (void)issueRedirectResponseForFile:file;
- (void)issueNotFoundResponse;
@end

NSString* const appBundleDirectURLPrefix = @"app-bundle:///direct";
NSString* const appBundleRedirectURLPrefix = @"app-bundle:///redirect";
static NSString* pathPrefix;
static UIWebView* uiwebview;

#pragma mark AppBundle

@implementation AppBundle

- (CDVPlugin*)initWithWebView:(UIWebView*)theWebView
{
    self = [super initWithWebView:theWebView];
    uiwebview = theWebView;
    if (self) {
        [NSURLProtocol registerClass:[AppBundleURLProtocol class]];
        pathPrefix = [[NSBundle mainBundle] pathForResource:@"cordova.js" ofType:@"" inDirectory:@"www"];
        NSRange range = [pathPrefix rangeOfString:@"/www/"];
        //trim trailing slash after www
        range.length--;
        pathPrefix = [[pathPrefix substringToIndex:NSMaxRange(range)] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]];
    }
    return self;
}

@end

#pragma mark AppBundleURLProtocol

@implementation AppBundleURLProtocol


+ (BOOL)canInitWithRequest:(NSURLRequest*)request
{
    NSURL* url = [request URL];
    NSString* urlString = [url absoluteString];
    return [urlString hasPrefix:appBundleRedirectURLPrefix] || [urlString hasPrefix:appBundleDirectURLPrefix];
}

+ (NSURLRequest*)canonicalRequestForRequest:(NSURLRequest*)request
{
    return request;
}

- (void)issueNotFoundResponse
{
    NSURL* url = [[self request] URL];
    NSURLResponse *response = [[NSHTTPURLResponse alloc] initWithURL:url statusCode:404 HTTPVersion:@"HTTP/1.1" headerFields:@{}];
    [[self client] URLProtocol:self didReceiveResponse:response cacheStoragePolicy:NSURLCacheStorageNotAllowed];
    [[self client] URLProtocolDidFinishLoading:self];
}

- (void)issueNSURLResponseForFile:file
{
    NSURL* url = [[self request] URL];
    NSString* path = [NSString stringWithFormat:@"%@%@", pathPrefix, file];
    FILE* fp = fopen([path UTF8String], "r");
    if (fp) {
        NSURLResponse *response = [[NSHTTPURLResponse alloc] initWithURL:url statusCode:200 HTTPVersion:@"HTTP/1.1" headerFields:@{}];
        [[self client] URLProtocol:self didReceiveResponse:response cacheStoragePolicy:NSURLCacheStorageNotAllowed];
        
        char buf[32768];
        size_t len;
        while ((len = fread(buf,1,sizeof(buf),fp))) {
            [[self client] URLProtocol:self didLoadData:[NSData dataWithBytes:buf length:len]];
        }
        fclose(fp);
        
        [[self client] URLProtocolDidFinishLoading:self];
        
    } else {
        [self issueNotFoundResponse];
    }
}

- (void)issueRedirectResponseForFile:file
{
    if([uiwebview isLoading]) {
        [uiwebview stopLoading];
    }
    NSString *newUrlString = [NSString stringWithFormat:@"file://%@%@", [pathPrefix stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding], file];
    NSURL *newUrl = [NSURL URLWithString:newUrlString];
    NSURLRequest *request = [NSURLRequest requestWithURL:newUrl];
    [uiwebview loadRequest:request];
}

- (void)startLoading
{
    NSURL *url = [[self request] URL];
    NSString* urlString = [url absoluteString];
    
    if([urlString hasPrefix:appBundleRedirectURLPrefix]){
        NSString* path = [urlString substringFromIndex:appBundleRedirectURLPrefix.length];
        [self issueRedirectResponseForFile:path];
    } else if([urlString hasPrefix:appBundleDirectURLPrefix]){
        NSString* path = [urlString substringFromIndex:appBundleDirectURLPrefix.length];
        [self issueNSURLResponseForFile:path];
    } else {
        [self issueNotFoundResponse];
    }
}

- (void)stopLoading
{
    // do any cleanup here
}

@end
