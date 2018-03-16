/*
 * Copyright © 2017 Mathieu Carbou (mathieu.carbou@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mycila.megatron.http;

import com.tc.classloader.CommonComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * @author Mathieu Carbou
 */
@CommonComponent
public class Http {

  private static final Logger LOGGER = LoggerFactory.getLogger(Http.class);

  public static void post(URL url, String text) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("[{}] POST > \n{}", url.getAuthority(), text);
    }

    try {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setUseCaches(false);
      connection.setConnectTimeout(5_000);
      connection.setReadTimeout(5_000);
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
      connection.setRequestProperty("Accept", "*/*");

      try (OutputStream os = connection.getOutputStream()) {
        os.write(text.getBytes(StandardCharsets.UTF_8));
        os.flush();
      }

      String status = connection.getResponseMessage();
      int code = connection.getResponseCode();
      if (code / 100 == 2) {
        LOGGER.trace("[{}] POST < {} {}", url.getAuthority(), code, status);
      } else {
        String err = "";
        try (InputStream is = connection.getInputStream()) {
          err += read(is);
        } catch (IOException ignored) {
        }
        try (InputStream is = connection.getErrorStream()) {
          err += read(is);
        } catch (IOException ignored) {
        }
        LOGGER.warn("[{}] POST < {} {}\n{}", url.getAuthority(), code, status, err);
      }
    } catch (IOException e) {
      LOGGER.warn("[{}] POST ERROR: {}", url.getAuthority(), e.getMessage(), e);
    }
  }

  private static String read(InputStream in) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = in.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toString(StandardCharsets.UTF_8.name());
  }

}
