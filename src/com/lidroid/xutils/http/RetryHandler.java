/*
 * Copyright (c) 2013. wyouflf (wyouflf@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lidroid.xutils.http;

import android.os.SystemClock;
import com.lidroid.xutils.util.LogUtils;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;

public class RetryHandler implements HttpRequestRetryHandler {
    private static final int RETRY_SLEEP_INTERVAL = 1000;

    //网络异常，继续
    private static HashSet<Class<?>> exceptionWhiteList = new HashSet<Class<?>>();

    //用户异常，不继续（如，用户中断线程）
    private static HashSet<Class<?>> exceptionBlackList = new HashSet<Class<?>>();

    static {
        exceptionWhiteList.add(NoHttpResponseException.class);
        exceptionWhiteList.add(UnknownHostException.class);
        exceptionWhiteList.add(SocketException.class);

        exceptionBlackList.add(InterruptedIOException.class);
        exceptionBlackList.add(SSLHandshakeException.class);
    }

    private final int maxRetries;

    public RetryHandler(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public boolean retryRequest(IOException exception, int retriedTimes, HttpContext context) {
        boolean retry = true;

        Boolean b = (Boolean) context.getAttribute(ExecutionContext.HTTP_REQ_SENT);
        boolean sent = (b != null && b.booleanValue());

        if (retriedTimes > maxRetries) {
            // 尝试次数超过用户定义的测试，默认5次
            retry = false;
        } else if (exceptionBlackList.contains(exception.getClass())) {
            // 线程被用户中断，则不继续尝试
            retry = false;
        } else if (exceptionWhiteList.contains(exception.getClass())) {
            retry = true;
        } else if (!sent) {
            retry = true;
        }

        if (retry) {
            try {
                Object currRequest = context.getAttribute(ExecutionContext.HTTP_REQUEST);
                if (currRequest != null) {
                    if (currRequest instanceof HttpRequestBase) {
                        HttpRequestBase requestBase = (HttpRequestBase) currRequest;
                        retry = requestBase != null && "GET".equals(requestBase.getMethod());
                    } else if (currRequest instanceof RequestWrapper) {
                        RequestWrapper requestWrapper = (RequestWrapper) currRequest;
                        retry = requestWrapper != null && "GET".equals(requestWrapper.getMethod());
                    }
                } else {
                    LogUtils.e("retry error, curr request is null");
                }
            } catch (Exception e) {
                retry = false;
                LogUtils.e("retry error", e);
            }
        }

        if (retry) {
            //休眠1秒钟后再继续尝试
            SystemClock.sleep(RETRY_SLEEP_INTERVAL);
        } else {
            LogUtils.e(exception.getMessage(), exception);
        }

        return retry;
    }

}